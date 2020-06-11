package pl.rpw.core.vm

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.TaskFinishedMessage
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage, TaskSpecification}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class VirtualMachineActor(val id: String,
                          val cpu: Int,
                          val memory: Int,
                          val diskSpace: Int)
  extends Actor {

  private var vm = VMRepository.findById(id)
  private val tasks = new mutable.HashSet[TaskSpecification]

  override def receive: Receive = {
    case TaskMessage(specification) =>
      println("Received task specification: " + specification)
      tasks.add(specification)
      execute(specification)

    case MigrationMessage(newHypervisor) =>
      println("Received migration order: " + newHypervisor)
      val actorSystem = ActorSystem()
      startMigration(actorSystem)
      migrateToNewHypervisor(newHypervisor, actorSystem)
  }

  private def migrateToNewHypervisor(newHypervisor: String, actorSystem: ActorSystem) = {
    vm.hypervisor = newHypervisor
    VMRepository.update(vm)
    val destHypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    // TODO find new hypervisor
    destHypervisor ! AttachVMMessage(this.id)
  }

  private def startMigration(actorSystem: ActorSystem): Unit = {
    vm.state = VMState.MIGRATING.toString
    VMRepository.update(vm)
    val hypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    hypervisor ! DetachVMMessage(this.id)
  }

  def allocateResources(cpu: Int, ram: Int, disk: Int): Unit = {
    vm.freeCpu += cpu
    vm.freeRam += ram
    vm.freeDisk += disk
    vm.state = VMState.ACTIVE.toString
    VMRepository.update(vm)
  }

  def freeResources(cpu: Int, ram: Int, disk: Int): Unit = {
    vm.freeCpu -= cpu
    vm.freeRam -= ram
    vm.freeDisk -= disk
    if (!vm.hasActivelyUsedResources()) {
      vm.state = VMState.IDLE.toString
    }
    VMRepository.update(vm)
  }

  def execute(specification: TaskSpecification): Unit = {
    if (!vm.state.equals(VMState.ACTIVE.toString)) {
      requestMachinesResources(vm)
    }
    allocateResources(specification.cpu, specification.ram, specification.disk)

    val task = new Runnable {
      def run(): Unit = {
        freeResources(specification.cpu, specification.ram, specification.disk)

        val actorSystem = ActorSystem()
        val localUtilityActor = actorSystem.actorSelection("user/" + specification.userId)
        localUtilityActor ! TaskFinishedMessage(specification.taskId)

        tasks.remove(specification)
        if (tasks.isEmpty) {
          freeMachinesResources(vm)
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

  private def freeMachinesResources(vm: VM) = {
    val actorSystem = ActorSystem()
    val hypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    hypervisor ! FreeResourcesMessage(id)
  }

  def requestMachinesResources(vm: VM) = {
    val actorSystem = ActorSystem()
    val hypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    hypervisor ! AllocateResourcesMessage(id)
  }

}
