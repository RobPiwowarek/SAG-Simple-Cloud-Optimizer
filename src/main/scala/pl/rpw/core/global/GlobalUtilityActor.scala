package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
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
      val hypervisor = HypervisorSelector.selectHypervisor(specification)
      val vm = createVM(userId, specification, hypervisor)
      val hypervisorRef = hypervisors.get(hypervisor.id).orNull
      hypervisorRef ! AttachVMMessage(vm.id)

    case TaskRequestMessage(userId, specification) =>
      val vm = VMSelector.selectVM(userId, specification)
      val vmRef = VMs.get(vm.id).orNull
      vmRef ! TaskMessage(specification)

    case OverprovisioningMessage(hypervisor) =>
      migrateMostRelevantMachineIfPossible(HypervisorRepository.findById(hypervisor))

    case UnderprovisioningMessage(hypervisor) =>
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
      .sortWith(VMSelector.getOrderFunction(mostExploitedResource))
      .map(vm => {
        val activeHypervisor = HypervisorSelector.selectDestinationHypervisorFrom(
            new VirtualMachineSpecification(vm.cpu, vm.ram, vm.disk), activeHypervisors, mostExploitedResource)
        if (activeHypervisor == null) {
          val idleHypervisor = HypervisorSelector.selectDestinationHypervisorFrom(
            new VirtualMachineSpecification(vm.cpu, vm.ram, vm.disk), idleHypervisors, mostExploitedResource)
          if (idleHypervisor == null) {
            // TODO cannot do the migration
          }
          idleHypervisor.freeCpu -= vm.cpu
          idleHypervisor.freeRam -= vm.ram
          idleHypervisor.freeDisk -= vm.disk
          return Tuple2[VM, Hypervisor](vm, idleHypervisor)
        } else {
          activeHypervisor.freeCpu -= vm.cpu
          activeHypervisor.freeRam -= vm.ram
          activeHypervisor.freeDisk -= vm.disk
          return Tuple2[VM, Hypervisor](vm, activeHypervisor)
        }
      })

    // TODO check validity of migration
    if (migrationSimulation.filter(...).isEmpty) {

    }
    // do the migration
  }

}
