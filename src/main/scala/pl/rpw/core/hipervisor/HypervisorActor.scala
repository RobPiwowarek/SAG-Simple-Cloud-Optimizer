package pl.rpw.core.hipervisor

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.OverprovisioningMessage
import pl.rpw.core.hipervisor.message._

class HypervisorActor(val cpu: Int,
                      val memory: Integer,
                      val diskSpace: Integer)
                     (implicit val actorSystem: ActorSystem)
  extends Actor {
  private var usedResources: Map[ResourceType.Value, Int] = Map(ResourceType.CPU -> 0, ResourceType.DISK_SPACE -> 0, ResourceType.MEMORY -> 0)

  private var virtualMachines = Map[ActorRef, VirtualMachineSpecification]()
  private val overprovisioningThreshold = 0.90
  private val underprovisioningThreshold = 0.70

  override def receive: Receive = {
    case AttachVMMessage(virtualMachine, specification) =>
      println("Received attach: " + virtualMachine + ", " + specification)

      allocateResources(
        specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE)
      )

      virtualMachines = virtualMachines + (virtualMachine -> specification)

    case DetachVMMessage(virtualMachine) =>
      println("Received detach: " + virtualMachine)

      val specification = virtualMachines.get(virtualMachine).orNull

      freeResources(
        specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE)
      )

      virtualMachines = virtualMachines - virtualMachine

    case AllocateResourcesMessage(virtualMachine) =>
      println("Received allocate: " + virtualMachine)

      val specification = virtualMachines(virtualMachine)

      allocateResources(
        specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE)
      )

    case FreeResourcesMessage(virtualMachine) =>
      println("Received free: " + virtualMachine)

      val specification = virtualMachines(virtualMachine)

      freeResources(
        specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE)
      )
  }

  def checkOverprovisioningAndNotifyGlobalAgent(): Unit = {
    val cpuUsage = usedResources(ResourceType.CPU) / cpu
    val memoryUsage = usedResources(ResourceType.MEMORY) / memory
    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace

    if (cpuUsage > overprovisioningThreshold || memoryUsage > overprovisioningThreshold || diskUsage > overprovisioningThreshold) {
      val globalUtilityActor = actorSystem.actorSelection("global_utility")
      globalUtilityActor ! OverprovisioningMessage(self)
    }
  }

  def checkUnderprovisioningAndNotifyGlobalAgent(): Unit = {
    val cpuUsage = usedResources(ResourceType.CPU) / cpu
    val memoryUsage = usedResources(ResourceType.MEMORY) / memory
    val diskUsage = usedResources(ResourceType.DISK_SPACE) / diskSpace

    if (cpuUsage < underprovisioningThreshold || memoryUsage < underprovisioningThreshold || diskUsage < underprovisioningThreshold) {
      val globalUtilityActor = actorSystem.actorSelection("global_utility")
      globalUtilityActor ! OverprovisioningMessage(self)
    }
  }

  def allocateResources(cpu: Int,
                        memory: Int,
                        diskSpace: Int): Unit = {
    usedResources = Map(
      ResourceType.CPU -> (usedResources(ResourceType.CPU) + cpu),
      ResourceType.MEMORY -> (usedResources(ResourceType.MEMORY) + memory),
      ResourceType.DISK_SPACE -> (usedResources(ResourceType.DISK_SPACE) + diskSpace)
    )

    checkOverprovisioningAndNotifyGlobalAgent()
  }

  def freeResources(cpu: Int,
                    memory: Int,
                    diskSpace: Int): Unit = {
    usedResources = Map(
      ResourceType.CPU -> (usedResources(ResourceType.CPU) - cpu),
      ResourceType.MEMORY -> (usedResources(ResourceType.MEMORY) - memory),
      ResourceType.DISK_SPACE -> (usedResources(ResourceType.DISK_SPACE) - diskSpace)
    )

    checkUnderprovisioningAndNotifyGlobalAgent()
  }

  def canRunVM(specification: VirtualMachineSpecification): Boolean = {
    val sufficientCpu = specification.resources(ResourceType.CPU) < cpu - usedResources(ResourceType.CPU)
    val sufficientMemory = specification.resources(ResourceType.MEMORY) < cpu - usedResources(ResourceType.MEMORY)
    val sufficientDiskSpace = specification.resources(ResourceType.DISK_SPACE) < cpu - usedResources(ResourceType.DISK_SPACE)

    sufficientCpu && sufficientMemory && sufficientDiskSpace
  }
}
