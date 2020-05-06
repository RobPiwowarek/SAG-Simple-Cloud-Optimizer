package pl.rpw.core.hipervisor

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.OverprovisioningMessage
import pl.rpw.core.hipervisor.message.{AllocateResourcesMessage, AttachVMMessage, DetachVMMessage, FreeResourcesMessage, VirtualMachineSpecification}

import scala.collection.mutable

class HipervisorActor(val cpu: Int, val memory: Integer, val diskSpace: Integer) extends Actor {
  val usedResources = mutable.Map(ResourceType.CPU -> 0, ResourceType.DISK_SPACE -> 0, ResourceType.MEMORY -> 0)

  private val virtualMachines = new mutable.HashMap[ActorRef, VirtualMachineSpecification]
  private val overprovisioningTreshold = 0.90
  private val underprovisioningTreshold = 0.70

  override def receive: Receive = {
    case AttachVMMessage(virtualMachine, specification) =>
      println("Received attach: " + virtualMachine + ", " + specification)
      allocateResources(specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE))
      virtualMachines.put(virtualMachine, specification)

    case DetachVMMessage(virtualMachine) =>
      println("Received detach: " + virtualMachine)
      val specification = virtualMachines.get(virtualMachine).orNull
      freeResources(specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE))
      virtualMachines.remove(virtualMachine)

    case AllocateResourcesMessage(virtualMachine) =>
      println("Received allocate: " + virtualMachine)
      val specification = virtualMachines.get(virtualMachine).orNull
      allocateResources(specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE))

    case FreeResourcesMessage(virtualMachine) =>
      println("Received free: " + virtualMachine)
      val specification = virtualMachines.get(virtualMachine).orNull
      freeResources(specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE))

  }

  def checkOverprovisioningAndNotifyGlobalAgent(): Unit = {
    val cpuUsage = usedResources(ResourceType.CPU) / cpu
    val memoryUsage = usedResources(ResourceType.MEMORY) / memory
    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace

    if (cpuUsage > overprovisioningTreshold || memoryUsage > overprovisioningTreshold || diskUsage > overprovisioningTreshold) {
      val actorSystem = ActorSystem()
      val globalUtilityActor = actorSystem.actorSelection("global_utility")
      globalUtilityActor ! OverprovisioningMessage(this.self)
    }
  }

  def checkUnderprovisioningAndNotifyGlobalAgent(): Unit = {
    val cpuUsage = usedResources(ResourceType.CPU) / cpu
    val memoryUsage = usedResources(ResourceType.MEMORY) / memory
    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace

    if (cpuUsage < underprovisioningTreshold || memoryUsage < underprovisioningTreshold || diskUsage < underprovisioningTreshold) {
      val actorSystem = ActorSystem()
      val globalUtilityActor = actorSystem.actorSelection("global_utility")
      globalUtilityActor ! OverprovisioningMessage(this.self)
    }
  }

  def allocateResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) += cpu
    usedResources(ResourceType.MEMORY) += memory
    usedResources(ResourceType.DISK_SPACE) += diskSpace

    checkOverprovisioningAndNotifyGlobalAgent();
  }

  def freeResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) -= cpu
    usedResources(ResourceType.MEMORY) -= memory
    usedResources(ResourceType.DISK_SPACE) -= diskSpace

    checkUnderprovisioningAndNotifyGlobalAgent();
  }

  def canRunVM(specification: VirtualMachineSpecification): Boolean = {
    val sufficientCpu = specification.resources(ResourceType.CPU) < cpu - usedResources(ResourceType.CPU)
    val sufficientMemory = specification.resources(ResourceType.MEMORY) < cpu - usedResources(ResourceType.MEMORY)
    val sufficientDiskSpace = specification.resources(ResourceType.DISK_SPACE) < cpu - usedResources(ResourceType.DISK_SPACE)

    return sufficientCpu && sufficientMemory && sufficientDiskSpace
  }
}
