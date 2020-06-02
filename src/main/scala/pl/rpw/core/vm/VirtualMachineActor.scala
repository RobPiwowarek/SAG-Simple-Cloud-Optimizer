package pl.rpw.core.vm

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.TaskFinishedMessage
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage, TaskSpecification}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class VirtualMachineActor(val cpu: Int,
                          val memory: Int,
                          val diskSpace: Int,
                          var hypervisor: ActorRef)
  extends Actor {

  private val usedResources = mutable.Map(ResourceType.CPU -> 0, ResourceType.DISK_SPACE -> 0, ResourceType.RAM -> 0)
  private var active = true

  private val tasks = new mutable.HashSet[TaskSpecification]

  override def receive: Receive = {
    case TaskMessage(specification) =>
      println("Received task specification: " + specification)
      tasks.add(specification)
      execute(specification)

    case MigrationMessage(hypervisor) =>
      hypervisor ! DetachVMMessage(this.self)
      println("Received migration order: " + hypervisor)
      this.hypervisor = hypervisor

//      hypervisor ! AttachVMMessage(
//        this.self,
//        new VirtualMachineSpecification(
//          Map(ResourceType.CPU -> this.cpu, ResourceType.DISK_SPACE -> this.diskSpace, ResourceType.RAM -> this.memory)
//        )
//      )
  }

  def allocateResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) += cpu
    usedResources(ResourceType.RAM) += memory
    usedResources(ResourceType.DISK_SPACE) += diskSpace
  }

  def freeResources(cpu: Int, memory: Int, diskSpace: Int): Unit = {
    usedResources(ResourceType.CPU) -= cpu
    usedResources(ResourceType.RAM) -= memory
    usedResources(ResourceType.DISK_SPACE) -= diskSpace
  }

  def execute(specification: TaskSpecification): Unit = {
    if (!active)
      requestMachinesResources()

//    allocateResources(specification.resources(ResourceType.CPU),
//      specification.resources(ResourceType.RAM),
//      specification.resources(ResourceType.DISK_SPACE))

    val task = new Runnable {
      def run(): Unit = {
//        freeResources(specification.resources(ResourceType.CPU),
//          specification.resources(ResourceType.RAM),
//          specification.resources(ResourceType.DISK_SPACE))

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

    scheduler.scheduleOnce(
      new FiniteDuration(specification.time, TimeUnit.SECONDS)) {
      task.run()
    }
  }

  private def freeMachinesResources() = {
    hypervisor ! FreeResourcesMessage(this.self)
  }

  def requestMachinesResources() = {
    hypervisor ! AllocateResourcesMessage(this.self)
  }

  def canExecuteTask(specification: TaskSpecification): Boolean = {
//    val sufficientCpu = specification.resources(ResourceType.CPU) < cpu - usedResources(ResourceType.CPU)
//    val sufficientMemory = specification.resources(ResourceType.RAM) < cpu - usedResources(ResourceType.RAM)
//    val sufficientDiskSpace = specification.resources(ResourceType.DISK_SPACE) < cpu - usedResources(ResourceType.DISK_SPACE)
//
//    sufficientCpu && sufficientMemory && sufficientDiskSpace
    true
  }
}
