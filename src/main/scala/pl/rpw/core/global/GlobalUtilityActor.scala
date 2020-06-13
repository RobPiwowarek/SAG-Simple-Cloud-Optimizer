package pl.rpw.core.global

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.global.message._
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.local.message.{TaskCreationFailed, VmIsDeadMessage}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.task.{TaskSpecification, TaskSpecificationsRepository}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage}
import pl.rpw.core.{Consts, Utils}

import scala.collection.mutable
import scala.util.Random

class GlobalUtilityActor(actors: mutable.Map[String, ActorRef] = mutable.Map.empty)
  extends Actor with LazyLogging {
  private val actorSystem = context.system

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
            val hypervisorRef = Utils.getActorRef(actorSystem, hypervisor.id)
            hypervisorRef ! AttachVMMessage(vm.id)
          } catch {
            case exception:
              Throwable => logger.error(s"Exception occured when awaiting for resolving of hypervisor ${hypervisor.id} in VMRequest in GUA: ${exception.getMessage}")
              Utils.markHypervisorAsDeadRecursively(hypervisor, actorSystem)
          }
      }

    case TaskRequestMessage(specification) =>
      logger.info(specification + " requested")

      // enqueue task if no vm can handle it
      val vm = VMSelector.selectVM(specification.userId, specification)
      vm match {
        case Consts.EmptyVM =>
          logger.info(s"Empty VM returned for Task Request Message with specification $specification")
          TaskSpecificationsRepository.insert(specification.toEntity)
        case vm =>
          try {
            val vmRef = Utils.getActorRef(actorSystem, vm.id)
            val specificationEntity = specification.toEntity
            specificationEntity.vm = Some(vm.id)
            TaskSpecificationsRepository.insert(specificationEntity)
            vmRef ! TaskMessage(specification)
          } catch {
            case exception: Throwable =>
              logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} and task ${specification.taskId} in GUA: ${exception.getMessage}")
              Utils.markMachineAsDeadAndNotifyOwner(vm, actorSystem)
              this.self ! TaskRequestMessage(specification)
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
        TaskSpecificationsRepository.remove(taskId)
        val ref = Utils.getActorRef(actorSystem, userId)
        ref ! TaskFinishedMessage(taskId, userId)
      } catch {
        case exception: Throwable => logger.error(s"Exception occured when awaiting for resolving of actor $userId and task $taskId in GUA: ${exception.getMessage}")
      }

      for (task <- TaskSpecificationsRepository.findByUserAndVmIsNull(userId)) {
        val vm = VMSelector.selectVM(userId, task.toSpec)
        vm match {
          case Consts.EmptyVM =>
            println(s"No vm for $task")
          case anyVm =>
            task.vm = Some(anyVm.id)
            try {
              val vmRef = Utils.getActorRef(actorSystem, anyVm.id)
              TaskSpecificationsRepository.update(task)
              vmRef ! TaskMessage(task.toSpec)
            } catch {
              case exception: Throwable =>
                logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} and task ${task.taskId} in GUA: ${exception.getMessage}")
                Utils.markMachineAsDeadAndNotifyOwner(vm, actorSystem)
                // fixme do insert-or-update in message handler
                // and send message
            }
        }
      }
  }

  private def createVM(userId: String,
                       specification: VirtualMachineSpecification,
                       hypervisor: Hypervisor) = {
    val id = Random.alphanumeric.take(6).mkString("")
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
              val vmRef = Utils.getActorRef(actorSystem, vm.id)
              vmRef ! MigrationMessage(destinationHypervisor.id)
            } catch {
              case exception: Throwable =>
                logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} for migration in hypervisor ${hypervisor.id} in GUA: ${exception.getMessage}")
                Utils.markMachineAsDeadAndNotifyOwner(vm, actorSystem)
                this.self ! OverprovisioningMessage(hypervisor.id)
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
        try {
          Utils.getActorRef(actorSystem, vm.id) ! MigrationMessage(hypervisor.id)
        } catch {
          case exception: Throwable =>
            logger.error(s"Exception occured when awaiting for resolving of vm ${vm.id} for migration in hypervisor ${hypervisor.id} in GUA: ${exception.getMessage}")
            Utils.markMachineAsDeadAndNotifyOwner(vm, actorSystem)
        }
      }
    } else {
      println("Migration of machines on " + hypervisor.id + " not valid")
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
    initialVMs.foreach(vm => {
      try {
        Utils.getActorRef(actorSystem, vm.id)
      } catch {
        case exception: Throwable =>
          logger.error(s"Exception occurred while initializing virtual machines in GUA: ${exception.getMessage}")
          Utils.markMachineAsDeadAndNotifyOwner(vm, actorSystem)
      }
    })
  }

  private def initHypervisors(): Unit = {
    val initialHypervisors = HypervisorRepository.findAll()
    initialHypervisors.foreach(hypervisor => {
      try {
        Utils.getActorRef(actorSystem, hypervisor.id)
      } catch {
        case exception: Throwable =>
          logger.error(s"Exception occurred while initializing hypervisors in GUA: ${exception.getMessage}")
          Utils.markHypervisorAsDeadRecursively(hypervisor, actorSystem)
      }
    })
  }
}
