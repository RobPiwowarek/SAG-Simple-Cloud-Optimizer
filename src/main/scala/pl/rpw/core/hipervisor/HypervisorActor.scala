package pl.rpw.core.hipervisor

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.OverprovisioningMessage
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.vm.{VMRepository, VMState}

class HypervisorActor(val availableCpu: Int,
                      val availableRam: Int,
                      val availableDisk: Int,
                      val id: String,
                      var globalUtility: ActorRef = null) extends Actor {

  private val overprovisioningThreshold = 0.90
  private val underprovisioningThreshold = 0.70

  override def receive: Receive = {
    case AttachVMMessage(vmId) =>
      println("Received attach: " + vmId)
      val vm = VMRepository.findById(vmId)
      vm.state = VMState.IDLE.toString
      VMRepository.update(vm)

    case DetachVMMessage(vmId) =>
      println("Received detach: " + vmId)
      val VM = VMRepository.findById(vmId)
      freeResources(VM.cpu, VM.ram, VM.disk)

    case AllocateResourcesMessage(vmId) =>
      println("Received allocate: " + vmId)
      val VM = VMRepository.findById(vmId)
      allocateResources(VM.cpu, VM.ram, VM.disk)

    case FreeResourcesMessage(vmId) =>
      println("Received free: " + vmId)
      val VM = VMRepository.findById(vmId)
      freeResources(VM.cpu, VM.ram, VM.disk)
  }

  def checkOverprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    val cpuUsage = (availableCpu - hypervisor.freeCpu) / availableCpu
    val memoryUsage = (availableRam - hypervisor.freeRam) / availableRam
    val diskUsage = (availableDisk - hypervisor.freeDisk) / availableDisk

    if (cpuUsage > overprovisioningThreshold || memoryUsage > overprovisioningThreshold || diskUsage > overprovisioningThreshold) {
      globalUtility ! OverprovisioningMessage(self)
    }
  }

  def checkUnderprovisioningAndNotifyGlobalAgent(hypervisor: Hypervisor): Unit = {
    val cpuUsage = (availableCpu - hypervisor.freeCpu) / availableCpu
    val memoryUsage = (availableRam - hypervisor.freeRam) / availableRam
    val diskUsage = (availableDisk - hypervisor.freeDisk) / availableDisk

    if (cpuUsage < underprovisioningThreshold || memoryUsage < underprovisioningThreshold || diskUsage < underprovisioningThreshold) {
      globalUtility ! OverprovisioningMessage(self)
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
    if (hypervisor.freeCpu == hypervisor.cpu
      && hypervisor.freeRam == hypervisor.ram
      && hypervisor.freeDisk == hypervisor.disk) {
      hypervisor.state = HypervisorState.IDLE.toString
    }
    HypervisorRepository.update(hypervisor)
    checkUnderprovisioningAndNotifyGlobalAgent(hypervisor)
  }
  
}
