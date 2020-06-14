package pl.rpw.core.hipervisor

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.Utils
import pl.rpw.core.global.message.{OverprovisioningMessage, UnderprovisioningMessage}
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.local.message.{VMCreated, VmIsDeadMessage}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.vm.{VMRepository, VMState}

class HypervisorActor(val availableCpu: Int,
                      val availableRam: Int,
                      val availableDisk: Int,
                      val id: String) extends Actor with LazyLogging{
  val actorSystem = context.system

  override def receive: Receive = {
    case AttachVMMessage(vmId) =>
      logger.info(s"Hypervisor $id received attach: $vmId")
      val vm = VMRepository.findById(vmId)
      if (vm.state.equals(VMState.CREATED.toString)) {
        try {
          Utils.getActorRef(actorSystem, vm.user) ! VMCreated(vm.id)
        } catch {
          case exception: Throwable =>
            logger.error(s"Exception occured when awaiting for resolving of actor ${vm.user} in hypervisor $id: ${exception.getMessage}")
        }
      }
      if (vm.hasActivelyUsedResources) {
        vm.state = VMState.ACTIVE.toString
        allocateResources(vm.cpu, vm.ram, vm.disk)
      } else {
        vm.state = VMState.IDLE.toString
      }
      VMRepository.update(vm)

    case DetachVMMessage(vmId) =>
      logger.info(s"Hypervisor $id received detach: $vmId")
      val VM = VMRepository.findById(vmId)
      if (VM.state.equals(VMState.ACTIVE.toString)) {
        freeResources(VM.cpu, VM.ram, VM.disk)
      }

    case AllocateResourcesMessage(vmId) =>
      logger.info(s"Hypervisor $id received allocate resources: $vmId")
      val VM = VMRepository.findById(vmId)
      allocateResources(VM.cpu, VM.ram, VM.disk)

    case FreeResourcesMessage(vmId) =>
      logger.info(s"Hypervisor $id received free resources: $vmId")
      val VM = VMRepository.findById(vmId)
      freeResources(VM.cpu, VM.ram, VM.disk)

    case VmIsDeadMessage(vm, tasks) =>
      logger.info(s"Hypervisor $id received vm is dead: $vm")
      val VM = VMRepository.findById(vm)
      if (VM.state.equals(VMState.ACTIVE.toString)) {
        freeResources(VM.cpu, VM.ram, VM.disk)
      }

  }

  def checkOverprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    if (hypervisor.isOverprovisioning) {
      try {
        Utils.globalUtility(actorSystem) ! OverprovisioningMessage(this.id)
      } catch {
        case exception: Throwable =>
          logger.error(s"Exception occured when awaiting for resolving GUA in Hypervisor $id when overprovisioning occurred: ${exception.getMessage}")
      }
    }
  }

  def checkUnderprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    if (hypervisor.isUnderprovisioning) {
      try {
        Utils.globalUtility(actorSystem) ! UnderprovisioningMessage(this.id)
      } catch {
        case exception: Throwable =>
          logger.error(s"Exception occured when awaiting for resolving GUA in Hypervisor $id when underprovisioning occurred: ${exception.getMessage}")
      }
    }
  }


  def allocateResources(cpu: Int,
                        ram: Int,
                        disk: Int): Unit = {
    val hypervisor = HypervisorRepository.findById(id)
    hypervisor.freeCpu -= cpu
    hypervisor.freeRam -= ram
    hypervisor.freeDisk -= disk
    if (hypervisor.state.equals(HypervisorState.IDLE.toString)) {
      hypervisor.state = HypervisorState.ACTIVE.toString
    }
    HypervisorRepository.update(hypervisor)
    checkOverprovisioningAndNotifyGlobalAgent(hypervisor)
  }

  def freeResources(cpu: Int,
                    ram: Int,
                    disk: Int): Unit = {
    val hypervisor = HypervisorRepository.findById(id)
    hypervisor.freeCpu += cpu
    hypervisor.freeRam += ram
    hypervisor.freeDisk += disk
    if (!hypervisor.hasUsedResources) {
      hypervisor.state = HypervisorState.IDLE.toString
    }
    HypervisorRepository.update(hypervisor)
    checkUnderprovisioningAndNotifyGlobalAgent(hypervisor)
  }

}
