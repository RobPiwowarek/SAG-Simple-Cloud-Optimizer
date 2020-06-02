package pl.rpw.core.hipervisor

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.OverprovisioningMessage
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.persistance.vm.{VMRepository, VMState}

class HypervisorActor(val cpu: Int,
                      val memory: Int,
                      val diskSpace: Int) extends Actor {
  //  private var virtualMachines = Map[ActorRef, VirtualMachineSpecification]()
  //  private val overprovisioningThreshold = 0.90
  //  private val underprovisioningThreshold = 0.70

  override def receive: Receive = {
    case AttachVMMessage(vmId, specification) =>
      println("Received attach: " + vmId + ", " + specification)
      val vm = VMRepository.findById(vmId)
      vm.state = VMState.IDLE.toString
      VMRepository.update(vm)
    //
    //    case DetachVMMessage(virtualMachine) =>
    //      println("Received detach: " + virtualMachine)
    //
    //      val specification = virtualMachines.get(virtualMachine).orNull

    //      freeResources(
    //        specification.resources(ResourceType.CPU),
    //        specification.resources(ResourceType.RAM),
    //        specification.resources(ResourceType.DISK_SPACE)
    //      )

    //      virtualMachines = virtualMachines - virtualMachine
    //
    //    case AllocateResourcesMessage(virtualMachine) =>
    //      println("Received allocate: " + virtualMachine)
    //
    //      val specification = virtualMachines(virtualMachine)

    //      allocateResources(
    //        specification.resources(ResourceType.CPU),
    //        specification.resources(ResourceType.RAM),
    //        specification.resources(ResourceType.DISK_SPACE)
    //      )

    //    case FreeResourcesMessage(virtualMachine) =>
    //      println("Received free: " + virtualMachine)
    //
    //      val specification = virtualMachines(virtualMachine)
    //
    //      freeResources(
    //        specification.resources(ResourceType.CPU),
    //        specification.resources(ResourceType.RAM),
    //        specification.resources(ResourceType.DISK_SPACE)
    //      )
  }

  //  def checkOverprovisioningAndNotifyGlobalAgent(): Unit = {
  //    val cpuUsage = usedResources(ResourceType.CPU) / cpu
  //    val memoryUsage = usedResources(ResourceType.RAM) / memory
  //    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace
  //
  //    if (cpuUsage > overprovisioningThreshold || memoryUsage > overprovisioningThreshold || diskUsage > overprovisioningThreshold) {
  //      val globalUtilityActor = actorSystem.actorSelection("global_utility")
  //      globalUtilityActor ! OverprovisioningMessage(self)
  //    }
  //  }
  //
  //  def checkUnderprovisioningAndNotifyGlobalAgent(): Unit = {
  //    val cpuUsage = usedResources(ResourceType.CPU) / cpu
  //    val memoryUsage = usedResources(ResourceType.RAM) / memory
  //    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace
  //
  //    if (cpuUsage < underprovisioningThreshold || memoryUsage < underprovisioningThreshold || diskUsage < underprovisioningThreshold) {
  //      val globalUtilityActor = actorSystem.actorSelection("global_utility")
  //      globalUtilityActor ! OverprovisioningMessage(self)
  //    }
  //  }
  //
  //  def allocateResources(cpu: Int,
  //                        memory: Int,
  //                        diskSpace: Int): Unit = {
  //    usedResources = Map(
  //      ResourceType.CPU -> (usedResources(ResourceType.CPU) + cpu),
  //      ResourceType.RAM -> (usedResources(ResourceType.RAM) + memory),
  //      ResourceType.DISK_SPACE -> (usedResources(ResourceType.DISK_SPACE) + diskSpace)
  //    )
  //
  //    checkOverprovisioningAndNotifyGlobalAgent()
  //  }
  //
  //  def freeResources(cpu: Int,
  //                    memory: Int,
  //                    diskSpace: Int): Unit = {
  //    usedResources = Map(
  //      ResourceType.CPU -> (usedResources(ResourceType.CPU) - cpu),
  //      ResourceType.RAM -> (usedResources(ResourceType.RAM) - memory),
  //      ResourceType.DISK_SPACE -> (usedResources(ResourceType.DISK_SPACE) - diskSpace)
  //    )
  //
  //    checkUnderprovisioningAndNotifyGlobalAgent()
  //  }
  //
  //  def canRunVM(specification: VirtualMachineSpecification): Boolean = {
  //    val sufficientCpu = specification.resources(ResourceType.CPU) < cpu - usedResources(ResourceType.CPU)
  //    val sufficientMemory = specification.resources(ResourceType.RAM) < cpu - usedResources(ResourceType.RAM)
  //    val sufficientDiskSpace = specification.resources(ResourceType.DISK_SPACE) < cpu - usedResources(ResourceType.DISK_SPACE)
  //
  //    sufficientCpu && sufficientMemory && sufficientDiskSpace
  //  }
}
