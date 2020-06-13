package pl.rpw.core.hipervisor

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import pl.rpw.core.Utils
import pl.rpw.core.global.message.{OverprovisioningMessage, UnderprovisioningMessage}
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.local.message.VMCreated
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.vm.{VMRepository, VMState}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}

class HypervisorActor(val availableCpu: Int,
                      val availableRam: Int,
                      val availableDisk: Int,
                      val id: String) extends Actor {
  val actorSystem = context.system

  override def receive: Receive = {
    case AttachVMMessage(vmId) =>
      print("Hypervisor " + id + " ")
      println("Received attach: " + vmId)
      val vm = VMRepository.findById(vmId)
      if (vm.state.equals(VMState.CREATED.toString)) {
        Utils.getActorRef(actorSystem, vm.user) ! VMCreated(vm.id)
      }
      if (vm.hasActivelyUsedResources) {
        vm.state = VMState.ACTIVE.toString
      } else {
        vm.state = VMState.IDLE.toString
      }
      VMRepository.update(vm)

    case DetachVMMessage(vmId) =>
      print("Hypervisor " + id + " ")
      println("Received detach: " + vmId)
      val VM = VMRepository.findById(vmId)
      freeResources(VM.cpu, VM.ram, VM.disk)

    case AllocateResourcesMessage(vmId) =>
      print("Hypervisor " + id + " ")
      println("Received allocate: " + vmId)
      val VM = VMRepository.findById(vmId)
      allocateResources(VM.cpu, VM.ram, VM.disk)

    case FreeResourcesMessage(vmId) =>
      print("Hypervisor " + id + " ")
      println("Received free: " + vmId)
      val VM = VMRepository.findById(vmId)
      freeResources(VM.cpu, VM.ram, VM.disk)
  }

  def checkOverprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    if (hypervisor.isOverprovisioning) {
      Utils.globalUtility(actorSystem) ! OverprovisioningMessage(this.id)
    }
  }

  def checkUnderprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    if (hypervisor.isUnderprovisioning) {
      Utils.globalUtility(actorSystem) ! UnderprovisioningMessage(this.id)
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
