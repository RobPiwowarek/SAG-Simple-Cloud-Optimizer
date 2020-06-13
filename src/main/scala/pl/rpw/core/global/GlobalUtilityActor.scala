package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.global.message._
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.local.message.TaskCreationFailed
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.task.{TaskSpecification, TaskSpecificationsRepository}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage}
import pl.rpw.core.{Consts, Utils}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

class GlobalUtilityActor(actors: mutable.Map[String, ActorRef] = mutable.Map.empty)
  extends Actor with LazyLogging {
  private val actorSystem = context.system
  private val VMs = mutable.HashMap[String, ActorRef]() // change to paths?
  private val hypervisors = mutable.HashMap[String, ActorRef]()
  private val taskQueues = mutable.HashMap[String, mutable.Queue[TaskSpecification]]()

  initHypervisors()
  initVirtualMachines()

  override def receive: Receive = {
    case VirtualMachineRequestMassage(userId, specification) =>
      logger.info(specification + " requested from " + userId)
      val hypervisor = HypervisorSelector.selectHypervisor(specification)
      hypervisor match {
        case Consts.EmptyHypervisor =>
          logger.info(s"Could not find hypervisor for specification: $specification and userId: $userId")
        case hypervisor =>
          val vm = createVM(userId, specification, hypervisor)
          try {
            val hypervisorRef = Await.result(actorSystem.actorSelection(hypervisor.id).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
            hypervisorRef ! AttachVMMessage(vm.id)
          } catch {
            case exception: Throwable => logger.error(s"Exception occured when awaiting for resolving of hypervisor ${hypervisor.id} in VMRequest in GUA: ${exception.getMessage}")
          }
      }

    case TaskRequestMessage(specification) =>
      logger.info(specification + " requested")

      if (!taskQueues.keys.exists(_.equals(specification.userId))) {
        taskQueues.put(specification.userId, mutable.Queue.empty)
      }

      // enqueue task if no vm can handle it
      val vm = VMSelector.selectVM(specification.userId, specification)

      vm match {
        case Consts.EmptyVM =>
          logger.info(s"Empty VM returned for Task Request Message with specification $specification")
          sender() ! TaskCreationFailed(specification)
        case vm =>
          try {
            val vmRef = Await.result(actorSystem.actorSelection(vm.id).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
            if (vmRef == null) {
              TaskSpecificationsRepository.insert(specification)
              taskQueues(specification.userId).enqueue(specification)
            } else {
              vmRef ! TaskMessage(specification)
            }
          } catch {
            case exception: Throwable => logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} and task ${specification.taskId} in GUA: ${exception.getMessage}")
          }
      }

    case OverprovisioningMessage(hypervisor) =>
      logger.info("Overprovisioning from: " + hypervisor)
      migrateMostRelevantMachineIfPossible(HypervisorRepository.findById(hypervisor))

    case UnderprovisioningMessage(hypervisor) =>
      logger.info("Underprovisioning from: " + hypervisor)
      migrateAllMachinesIfPossible(HypervisorRepository.findById(hypervisor))

    case TaskFinishedMessage(taskId, userId) =>
      try {
        val ref = Await.result(actorSystem.actorSelection(userId).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
        if (!taskQueues.keys.exists(_.equals(userId))) {
          taskQueues.put(userId, mutable.Queue.empty)
        }

        for (task <- taskQueues(userId)) {
          val vm = VMSelector.selectVM(userId, task)
          val vmRef = Await.result(actorSystem.actorSelection(vm.id).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
          if (vmRef != null) {
            vmRef ! TaskMessage(task)
            TaskSpecificationsRepository.remove(taskId)
            taskQueues.update(userId, taskQueues(userId).filterNot(_.equals(task)))
          }
        }

        ref ! TaskFinishedMessage(taskId, userId)
      } catch {
        case exception: Throwable => logger.error(s"Exception occured when awaiting for resolving of actor $userId and task $taskId in GUA: ${exception.getMessage}")
      }
  }

  private def createVM(userId: String,
                       specification: VirtualMachineSpecification,
                       hypervisor: Hypervisor) = {
    val id = Random.alphanumeric.take(32).mkString("")
    val vmActor = actorSystem.actorOf(Props(
      new VirtualMachineActor(
        id,
        specification.cpu,
        specification.ram,
        specification.disk
      )
    ), id)

    actors.put(id, vmActor)

    val vm = VM(
      id,
      VMState.CREATED.toString,
      specification.cpu,
      specification.ram,
      specification.disk,
      userId,
      hypervisor.id,
      specification.cpu,
      specification.ram,
      specification.disk
    )

    VMRepository.insert(vm)
    VMs.put(id, vmActor)
    vm
  }

  def migrateMostRelevantMachineIfPossible(hypervisor: Hypervisor): Unit = {
    // choose VM that uses most of overprovisioned resource and check if migration is possible.
    // If so, select hipervisor by MaxMin strategy and migrate machine
    val vm = VMSelector.selectVMToMigrate(hypervisor)
    vm match {
      case Consts.EmptyVM =>
        logger.info(s"Couldnt find vm for migrating most relevant machine if possible, hypervisor: ${hypervisor.id}")
      case vm =>
        HypervisorSelector.selectDestinationHypervisorForMigration(vm.toSpecs, hypervisor) match {
          case Consts.EmptyHypervisor =>
            logger.info("Cannot migrate " + hypervisor)
          case destinationHypervisor =>
            try {
              val vmRef = Await.result(actorSystem.actorSelection(vm.id).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
              vmRef ! MigrationMessage(destinationHypervisor.id)
            } catch {
              case exception: Throwable => logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} and hypervisor ${hypervisor.id} in GUA: ${exception.getMessage}")
            }
        }
    }

  }

  def migrateAllMachinesIfPossible(hypervisor: Hypervisor): Unit = {
    // try migrating all machines to other hipervisors by using MaxMin strategy
    val vms = VMRepository.findByHypervisor(hypervisor.id)
    val activeHypervisors = HypervisorRepository.findByState(HypervisorState.ACTIVE).filterNot(_.equals(hypervisor))
    val idleHypervisors = HypervisorRepository.findByState(HypervisorState.IDLE).filterNot(_.equals(hypervisor))

    val mostExploitedResource = HypervisorSelector.selectMostExploitedResource(HypervisorRepository.findAll())
    val migrationSimulation: Seq[(VM, Hypervisor)] = vms
      .sortWith(Utils.getVmOrderingFunctionForMigration(mostExploitedResource))
      .map(vm => {
        HypervisorSelector.selectDestinationHypervisorFrom(vm.toSpecs, activeHypervisors, mostExploitedResource) match {
          case Consts.EmptyHypervisor =>
            HypervisorSelector.selectDestinationHypervisorFrom(vm.toSpecs, idleHypervisors, mostExploitedResource) match {
              case Consts.EmptyHypervisor =>
                println("Migration of machines on " + hypervisor + " not possible")
                return
              case idleHypervisor =>
                claimResources(vm, idleHypervisor)
            }
          case activeHypervisor =>
            claimResources(vm, activeHypervisor)
        }
      })

    migrateIfPossible(hypervisor, migrationSimulation)
  }

  private def migrateIfPossible(hypervisor: Hypervisor,
                                migrationSimulation: Seq[(VM, Hypervisor)]): Unit =
    if (doesItMakeSenseToPerformThisMigration(migrationSimulation)) {
      migrationSimulation.foreach { case (vm, hypervisor) =>
        VMs(vm.id) ! MigrationMessage(hypervisor.id)
      }
    } else {
      println("Migration of machines on " + hypervisor + " not valid")
    }

  private def doesItMakeSenseToPerformThisMigration(migrationSimulation: Seq[(VM, Hypervisor)]) = {
    migrationSimulation.map {
      case (_, hypervisor: Hypervisor) if hypervisor.isOverprovisioning || hypervisor.isUnderprovisioning => false
      case _ => true
    }.reduce((x, y) => x && y)
  }

  private def claimResources(vm: VM,
                             hypervisor: Hypervisor): (VM, Hypervisor) = {
    hypervisor.freeCpu -= vm.cpu
    hypervisor.freeRam -= vm.ram
    hypervisor.freeDisk -= vm.disk
    (vm, hypervisor)
  }

  private def initVirtualMachines(): Unit = {
    val initialVMs = VMRepository.findAll()
    try {
      initialVMs.foreach(vm => {
        val path = "user/" + vm.id
        val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
        VMs.put(vm.id, ref)
      })
    } catch {
      case exception: Throwable => logger.error(s"Exception occured while initialising virutal machines in GUA: ${exception.getMessage}")
    }
  }

  private def initHypervisors(): Unit = {
    val initialHypervisors = HypervisorRepository.findAll()
    try {
      initialHypervisors.foreach(h => {
        val path = "user/" + h.id
        val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
        hypervisors.put(h.id, ref)
      })
    } catch {
      case exception: Throwable => logger.error(s"Exception occured while initialising hypervisors in GUA: ${exception.getMessage}")
    }
  }
}
