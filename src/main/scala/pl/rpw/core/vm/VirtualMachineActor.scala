package pl.rpw.core.vm

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.TaskFinishedMessage
import pl.rpw.core.hipervisor.message.{AllocateResourcesMessage, AttachVMMessage, DetachVMMessage, FreeResourcesMessage, VirtualMachineSpecification}
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage, TaskSpecification}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class VirtualMachineActor(val cpu: Int, val memory: Int, val diskSpace: Int, var hipervisor: ActorRef) extends Actor {
  val usedResources = mutable.Map(ResourceType.CPU -> 0, ResourceType.DISK_SPACE -> 0, ResourceType.MEMORY -> 0)
  var active = true

  private val tasks = new mutable.HashSet[TaskSpecification]

  override def receive: Receive = {
    case TaskMessage(specification) =>
      println("Received task specification: " + specification)
      tasks.add(specification)
      execute(specification)
    case MigrationMessage(hipervisor) =>
      hipervisor ! DetachVMMessage(this.self)
      println("Received migration order: " + hipervisor)
      this.hipervisor = hipervisor
      hipervisor ! AttachVMMessage(this.self,
        new VirtualMachineSpecification(
          Map(ResourceType.CPU -> this.cpu, ResourceType.DISK_SPACE -> this.diskSpace, ResourceType.MEMORY -> this.memory)
        ))
  }

  def allocateResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) += cpu
    usedResources(ResourceType.MEMORY) += memory
    usedResources(ResourceType.DISK_SPACE) += diskSpace
  }

  def freeResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) -= cpu
    usedResources(ResourceType.MEMORY) -= memory
    usedResources(ResourceType.DISK_SPACE) -= diskSpace
  }

  def execute(specification: TaskSpecification): Unit = {
    if (!active)
      requestMachinesResources()

    allocateResources(specification.resources(ResourceType.CPU),
      specification.resources(ResourceType.MEMORY),
      specification.resources(ResourceType.DISK_SPACE))

    val task = new Runnable {
      def run(): Unit = {
        freeResources(specification.resources(ResourceType.CPU),
          specification.resources(ResourceType.MEMORY),
          specification.resources(ResourceType.DISK_SPACE))

        val actorSystem = ActorSystem()
        val globalUtilityActor = actorSystem.actorSelection("global_utility")
        globalUtilityActor ! TaskFinishedMessage(specification.taskId)

        if (tasks.isEmpty) {
          active = false
          freeMachinesResources()
        }
      }
    }

    val actorSystem = ActorSystem()
    val scheduler = actorSystem.scheduler
    implicit val executor = actorSystem.dispatcher
    scheduler.scheduleOnce(new FiniteDuration(specification.time, TimeUnit.SECONDS)){
      task.run()
    }
  }

  private def freeMachinesResources() = {
    hipervisor ! FreeResourcesMessage(this.self)
  }

  def requestMachinesResources() = {
    hipervisor ! AllocateResourcesMessage(this.self)
  }

  def canExecuteTask(specification: TaskSpecification): Boolean = {
    val sufficientCpu = specification.resources(ResourceType.CPU) < cpu - usedResources(ResourceType.CPU)
    val sufficientMemory = specification.resources(ResourceType.MEMORY) < cpu - usedResources(ResourceType.MEMORY)
    val sufficientDiskSpace = specification.resources(ResourceType.DISK_SPACE) < cpu - usedResources(ResourceType.DISK_SPACE)

    return sufficientCpu && sufficientMemory && sufficientDiskSpace
  }
}
