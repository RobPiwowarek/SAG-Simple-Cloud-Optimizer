package pl.rpw.core.vm

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import pl.rpw.core.Utils
import pl.rpw.core.global.message.{MigrationFailedMessage, TaskFinishedMessage}
import pl.rpw.core.hipervisor.message._
import pl.rpw.core.persistance.hypervisor.HypervisorRepository
import pl.rpw.core.persistance.task.TaskSpecification
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class VirtualMachineActor(val id: String,
                          val cpu: Int,
                          val memory: Int,
                          val diskSpace: Int)
  extends Actor {

  private val actorSystem = context.system
  private val tasks = new mutable.HashSet[TaskSpecification]
  private val retryTime: Long = 10

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
      val vm = VMRepository.findById(id)
      val oldHypervisor = vm.hypervisor.orNull
      startMigration(vm, oldHypervisor, actorSystem)
      migrateToNewHypervisor(newHypervisor, oldHypervisor, actorSystem)
  }

  def requestNewMigration(): Unit = {
    try {
      val globalUtilityActor = Utils.globalUtility(actorSystem)
      globalUtilityActor ! MigrationFailedMessage(id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving GUA in VM $id: ${exception.getMessage}")
        val scheduler = actorSystem.scheduler
        implicit val executor = actorSystem.dispatcher
        scheduler.scheduleOnce(
          new FiniteDuration(retryTime, TimeUnit.SECONDS)) {
          new Runnable {
            override def run(): Unit = requestNewMigration()
          }
        }
    }
  }

  def reattachToOldHypervisor(oldHypervisor: String) = {
    val vm = VMRepository.findById(id)
    vm.hypervisor = Some(oldHypervisor)
    VMRepository.update(vm)
    try {
      val hypervisor = Utils.getActorRef(actorSystem, oldHypervisor)
      hypervisor ! AttachVMMessage(this.id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving of hypervisor ${oldHypervisor} in VM ${vm.id}: ${exception.getMessage}")
        vm.hypervisor = null
        VMRepository.update(vm)
        Utils.markHypervisorAsDeadRecursively(HypervisorRepository.findById(oldHypervisor), actorSystem)
        requestNewMigration()
    }
  }

  private def migrateToNewHypervisor(newHypervisor: String, oldHypervisor: String, actorSystem: ActorSystem) = {
    val vm = VMRepository.findById(id)
    vm.hypervisor = Some(newHypervisor)
    VMRepository.update(vm)
    try {
      val destHypervisor = Utils.getActorRef(actorSystem, newHypervisor)
      destHypervisor ! AttachVMMessage(this.id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving of hypervisor ${vm.hypervisor} in VM ${vm.id}: ${exception.getMessage}")
        reattachToOldHypervisor(oldHypervisor)
    }
  }

  private def startMigration(vm: VM, oldHypervisor: String, actorSystem: ActorSystem): Unit = {
    vm.state = VMState.MIGRATING.toString
    vm.hypervisor = None
    VMRepository.update(vm)
    try {
      val hypervisor = Utils.getActorRef(actorSystem, oldHypervisor)
      hypervisor ! DetachVMMessage(this.id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving of hypervisor ${oldHypervisor} in VM ${vm.id}: ${exception.getMessage}")
        Utils.markHypervisorAsDeadRecursively(HypervisorRepository.findById(oldHypervisor), actorSystem)
    }
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
        sendTaskFinishedMessage(specification)

        tasks.remove(specification)
        if (tasks.isEmpty) {
          freeMachinesResources(vm)
        }
      }
    }

    val scheduler = actorSystem.scheduler
    implicit val executor = actorSystem.dispatcher
    scheduler.scheduleOnce(
      new FiniteDuration(specification.time, TimeUnit.SECONDS)) {
      task.run()
    }
  }

  private def sendTaskFinishedMessage(specification: TaskSpecification): Unit = {
    try {
      val globalUtilityActor = Utils.globalUtility(actorSystem)
      globalUtilityActor ! TaskFinishedMessage(specification.taskId, specification.userId)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving GUA in VM $id: ${exception.getMessage}")
        val scheduler = actorSystem.scheduler
        implicit val executor = actorSystem.dispatcher
        scheduler.scheduleOnce(
          new FiniteDuration(retryTime, TimeUnit.SECONDS)) {
          new Runnable {
            override def run(): Unit = sendTaskFinishedMessage(specification)
          }
        }
    }
  }

  private def freeMachinesResources(vm: VM) = {
    try {
      val hypervisor = Utils.getActorRef(actorSystem, vm.hypervisor.orNull)
      hypervisor ! FreeResourcesMessage(id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving of hypervisor ${vm.hypervisor} in VM ${vm.id}: ${exception.getMessage}")
        Utils.markHypervisorAsDeadRecursively(HypervisorRepository.findById(vm.hypervisor.orNull), actorSystem)
    }

  }

  def requestMachinesResources(vm: VM) = {
    try {
      val hypervisor = Utils.getActorRef(actorSystem, vm.hypervisor.orNull)
      hypervisor ! AllocateResourcesMessage(id)
    } catch {
      case exception: Throwable =>
        println(s"Exception occured when awaiting for resolving of hypervisor ${vm.hypervisor} in VM ${vm.id}: ${exception.getMessage}")
        Utils.markHypervisorAsDeadRecursively(HypervisorRepository.findById(vm.hypervisor.orNull), actorSystem)
    }
  }

}
