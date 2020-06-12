package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import pl.rpw.core.global.message._
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage}
import pl.rpw.core.{Consts, Utils}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

class GlobalUtilityActor(actors: mutable.Map[String, ActorRef] = mutable.Map.empty)
  extends Actor {
  private val actorSystem = context.system
  private val VMs = mutable.HashMap[String, ActorRef]() // change to paths?
  private val hypervisors = mutable.HashMap[String, ActorRef]()

  initHypervisors()
  initVirtualMachines()

  override def receive: Receive = {
    case VirtualMachineRequestMassage(userId, specification) =>
      println(specification + " requested from " + userId)
      val hypervisor = HypervisorSelector.selectHypervisor(specification)
      if (hypervisor == null) {
        // queue? send back a message?
      }
      val vm = createVM(userId, specification, hypervisor)
      val hypervisorRef = hypervisors.get(hypervisor.id).orNull // fixme: Paula, czy to moze byc faktycznie nullem?, jesli tak to jak handlujemy taka sytuacje
      hypervisorRef ! AttachVMMessage(vm.id)

    case TaskRequestMessage(specification) =>
      println(specification + " requested")
      val vm = VMSelector.selectVM(specification.userId, specification)
      val vmRef = VMs.get(vm.id).orNull //fixme: same here
      vmRef ! TaskMessage(specification)

    case OverprovisioningMessage(hypervisor) =>
      println("Overprovisioning from: " + hypervisor)
      migrateMostRelevantMachineIfPossible(HypervisorRepository.findById(hypervisor))

    case UnderprovisioningMessage(hypervisor) =>
      println("Underprovisioning from: " + hypervisor)
      migrateAllMachinesIfPossible(HypervisorRepository.findById(hypervisor))

    case TaskFinishedMessage(taskId, userId) =>
      val ref = Await.result(actorSystem.actorSelection(userId).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
      ref ! TaskFinishedMessage(taskId, userId)
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
    HypervisorSelector.selectDestinationHypervisorForMigration(vm.toSpecs, hypervisor) match {
      case Consts.EmptyHypervisor =>
        println("Cannot migrate " + hypervisor)
      // todo:
      case destinationHypervisor =>
        val vmRef = VMs.get(vm.id).orNull
        vmRef ! MigrationMessage(destinationHypervisor.id)
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
    initialVMs.foreach(vm => {
      val path = "user/" + vm.id
      val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
      VMs.put(vm.id, ref)
    })
  }

  private def initHypervisors(): Unit = {
    val initialHypervisors = HypervisorRepository.findAll()
    initialHypervisors.foreach(h => {
      val path = "user/" + h.id
      val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
      hypervisors.put(h.id, ref)
    })
  }
}
