package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.VMSelector.{compareCpu, compareDisk, compareRam}
import pl.rpw.core.global.message._
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{MigrationMessage, TaskMessage}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

class GlobalUtilityActor(var actors: mutable.Map[String, ActorRef] = null)
  extends Actor {
  private val actorSystem = context.system
  private val VMs = mutable.HashMap[String, ActorRef]()
  private val hypervisors = mutable.HashMap[String, ActorRef]()

  initHypervisors()
  initVirtualMachines()

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

  override def receive: Receive = {
    case VirtualMachineRequestMassage(userId, specification) =>
      println(specification + " requested from " + userId)
      val hypervisor = HypervisorSelector.selectHypervisor(specification)
      val vm = createVM(userId, specification, hypervisor)
      val hypervisorRef = hypervisors.get(hypervisor.id).orNull
      hypervisorRef ! AttachVMMessage(vm.id)

    case TaskRequestMessage(specification) =>
      println(specification + " requested")
      val vm = VMSelector.selectVM(specification.userId, specification)
      val vmRef = VMs.get(vm.id).orNull
      vmRef ! TaskMessage(specification)

    case OverprovisioningMessage(hypervisor) =>
      println("Overprovisioning from: " + hypervisor)
      migrateMostRelevantMachineIfPossible(HypervisorRepository.findById(hypervisor))

    case UnderprovisioningMessage(hypervisor) =>
      println("Underprovisioning from: " + hypervisor)
      migrateAllMachinesIfPossible(HypervisorRepository.findById(hypervisor))

    case TaskFinishedMessage(_) =>
    //respond to local agent about task execution
  }

  private def createVM(userId: String,
                       specification: VirtualMachineSpecification,
                       hypervisor: Hypervisor) = {
    val id = Random.alphanumeric.take(32).mkString("")
    val ref = actorSystem.actorOf(Props(
      new VirtualMachineActor(id, specification.cpu, specification.ram, specification.disk)), id)
    if (actors != null) {
      actors.put(id, ref)
    }

    val vm = VM(id, VMState.CREATED.toString, specification.cpu, specification.ram, specification.disk,
      userId, hypervisor.id, specification.cpu, specification.ram, specification.disk)
    VMRepository.insert(vm)
    VMs.put(id, ref)
    vm
  }

  def migrateMostRelevantMachineIfPossible(hypervisor: Hypervisor): Unit = {
    // choose VM that uses most of overprovisioned resource and check if migration is possible.
    // If so, select hipervisor by MaxMin strategy and migrate machine
    val vm = VMSelector.selectVMToMigrate(hypervisor)
    val destinationHypervisor = HypervisorSelector.selectDestinationHypervisorForMigration(new VirtualMachineSpecification(vm.cpu, vm.ram, vm.disk), hypervisor)
    if (destinationHypervisor != null) {
      val vmRef = VMs.get(vm.id).orNull
      vmRef ! MigrationMessage(destinationHypervisor.id)
    } else {
      println("Cannot migrate " + hypervisor)
      // TODO
    }
  }

  def migrateAllMachinesIfPossible(hypervisor: Hypervisor): Unit = {
    // try migrating all machines to other hipervisors by using MaxMin strategy
    val vms = VMRepository.findByHypervisor(hypervisor.id)
    val activeHypervisors = HypervisorRepository.findByState(HypervisorState.ACTIVE).filterNot(_.equals(hypervisor))
    val idleHypervisors = HypervisorRepository.findByState(HypervisorState.IDLE).filterNot(_.equals(hypervisor))

    val mostExploitedResource = HypervisorSelector.selectMostExploitedResource(HypervisorRepository.findAll())
    val migrationSimulation = vms
      .sortWith(getOrderingFunction(mostExploitedResource))
      .map(vm => {
        val activeHypervisor = HypervisorSelector.selectDestinationHypervisorFrom(
            new VirtualMachineSpecification(vm.cpu, vm.ram, vm.disk), activeHypervisors, mostExploitedResource)
        if (activeHypervisor == null) {
          val idleHypervisor = HypervisorSelector.selectDestinationHypervisorFrom(
            new VirtualMachineSpecification(vm.cpu, vm.ram, vm.disk), idleHypervisors, mostExploitedResource)
          if (idleHypervisor == null) {
            println("Migration of machines on " + hypervisor + " not possible")
            return
          }
          idleHypervisor.freeCpu -= vm.cpu
          idleHypervisor.freeRam -= vm.ram
          idleHypervisor.freeDisk -= vm.disk
          (vm, idleHypervisor)
        } else {
          activeHypervisor.freeCpu -= vm.cpu
          activeHypervisor.freeRam -= vm.ram
          activeHypervisor.freeDisk -= vm.disk
          (vm, activeHypervisor)
        }
      })

    val doesItMakeSenseToPerformThisMigration: Boolean = migrationSimulation.map {
      case (_, hypervisor: Hypervisor) if hypervisor.isOverprovisioning || hypervisor.isUnderprovisioning =>
        false
      case _ =>
        true
    }.reduce((x, y) => x && y)

    if (doesItMakeSenseToPerformThisMigration) {
      migrationSimulation.foreach { case (vm, hypervisor) =>
        VMs(vm.id) ! MigrationMessage(hypervisor.id)
      }
    } else {
      println("Migration of machines on " + hypervisor + " not valid")
    }
  }

  private def getOrderingFunction(resource: ResourceType.Value) = {
    if (resource == ResourceType.CPU) {
      (vm1: VM, vm2: VM) => compareCpu(vm1, vm2)
    } else if (resource == ResourceType.RAM) {
      (vm1: VM, vm2: VM) => compareRam(vm1, vm2)
    } else {
      (vm1: VM, vm2: VM) => compareDisk(vm1, vm2)
    }
  }
}
