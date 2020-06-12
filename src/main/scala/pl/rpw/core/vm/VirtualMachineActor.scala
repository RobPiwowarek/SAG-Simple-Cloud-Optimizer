package pl.rpw.core.vm

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import pl.rpw.core.global.message.TaskFinishedMessage
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage, TaskSpecification}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}

class VirtualMachineActor(val id: String,
                          val cpu: Int,
                          val memory: Int,
                          val diskSpace: Int)
  extends Actor {

  private val actorSystem = context.system
  private val tasks = new mutable.HashSet[TaskSpecification]

  override def receive: Receive = {
    case TaskMessage(specification) =>
      print("Virtual Machine " + id + " ")
      println("Received task specification: " + specification)
      tasks.add(specification)
      execute(specification)

    case MigrationMessage(newHypervisor) =>
      print("Virtual Machine " + id + " ")
      println("Received migration order: " + newHypervisor)
      val actorSystem = ActorSystem()
      startMigration(actorSystem)
      migrateToNewHypervisor(newHypervisor, actorSystem)
  }

  private def migrateToNewHypervisor(newHypervisor: String, actorSystem: ActorSystem) = {
    val vm = VMRepository.findById(id)
    vm.hypervisor = newHypervisor
    VMRepository.update(vm)
    val destHypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    // TODO find new hypervisor
    destHypervisor ! AttachVMMessage(this.id)
  }

  private def startMigration(actorSystem: ActorSystem): Unit = {
    val vm = VMRepository.findById(id)
    vm.state = VMState.MIGRATING.toString
    VMRepository.update(vm)
    val hypervisor = actorSystem.actorSelection("user/" + vm.hypervisor)
    hypervisor ! DetachVMMessage(this.id)
  }

  def allocateResources(cpu: Int, ram: Int, disk: Int): Unit = {
    val vm = VMRepository.findById(id)
    vm.freeCpu -= cpu
    vm.freeRam -= ram
    vm.freeDisk -= disk
    vm.state = VMState.ACTIVE.toString
    VMRepository.update(vm)
  }

  def freeResources(cpu: Int, ram: Int, disk: Int): Unit = {
    val vm = VMRepository.findById(id)
    vm.freeCpu += cpu
    vm.freeRam += ram
    vm.freeDisk += disk
    if (!vm.hasActivelyUsedResources) {
      vm.state = VMState.IDLE.toString
    }
    VMRepository.update(vm)
  }

  def execute(specification: TaskSpecification): Unit = {
    val vm = VMRepository.findById(id)
    if (!vm.state.equals(VMState.ACTIVE.toString)) {
      requestMachinesResources(vm)
    }
    allocateResources(specification.cpu, specification.ram, specification.disk)

    val task = new Runnable {
      def run(): Unit = {
        freeResources(specification.cpu, specification.ram, specification.disk)

        val actorSystem = ActorSystem()
        val localUtilityActor = actorSystem.actorSelection("user/" + specification.userId)
        localUtilityActor ! TaskFinishedMessage(specification.taskId, specification.userId)

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
    val hypervisor = Await.result(actorSystem.actorSelection("user/" + vm.hypervisor).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
    hypervisor ! FreeResourcesMessage(id)
  }

  def requestMachinesResources(vm: VM) = {
    val hypervisor = Await.result(actorSystem.actorSelection("user/" + vm.hypervisor).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
    hypervisor ! AllocateResourcesMessage(id)
  }

}
