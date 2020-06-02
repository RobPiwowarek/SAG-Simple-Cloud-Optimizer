package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.{OverprovisioningMessage, TaskFinishedMessage, TaskRequestMessage, UnderprovisioningMessage, VirtualMachineRequestMassage}
import pl.rpw.core.persistance.{Hypervisor, HypervisorRepository, VM, VMRepository, VMState}
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{TaskMessage, TaskSpecification}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

class GlobalUtilityActor(var actors: mutable.Map[String, ActorRef] = null)
                        extends Actor {
  private val actorSystem = context.system
  private val hypervisorRepository = new HypervisorRepository()
  private val vmRepository = new VMRepository()

  private val VMs = mutable.HashMap[String, ActorRef]()
  private val hypervisors = mutable.HashMap[String, ActorRef]()

  initHypervisors()
  initVirtualMachines()

  private def initVirtualMachines(): Unit = {
    val initialVMs = vmRepository.findAll()
    initialVMs.foreach(vm => {
      val id = vm.id
      val path = "vm/" + id
      val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
      VMs.put(id, ref)
    })
  }

  private def initHypervisors(): Unit = {
    val initialHypervisors = hypervisorRepository.findAll()
    initialHypervisors.foreach(h => {
      val id = h.id
      val path = "hypervisor/" + id
      val ref = Await.result(actorSystem.actorSelection(path).resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
      hypervisors.put(id, ref)
    })
  }

  override def receive: Receive = {
    case VirtualMachineRequestMassage(userId, specification) =>
      val hypervisor = HypervisorSelector.selectHypervisor(specification, hypervisorRepository)
      val vm = createVM(specification, hypervisor)
      val hypervisorRef = hypervisors.get(hypervisor.id).orNull

      hypervisorRef ! AttachVMMessage(vm, specification)
      addVmToMap(userId, vm)

    case TaskRequestMessage(userId, specification) =>
      val vm = selectVM(userId, specification)
      vm ! TaskMessage(specification)

    case OverprovisioningMessage(hypervisor) =>
      migrateMostRelevantMachineIfPossible(hypervisor)

    case UnderprovisioningMessage(hypervisor) =>
      migrateAllMachinesIfPossible(hypervisor)

    case TaskFinishedMessage(_) =>
      //respond to local agent about task execution
  }

  private def addVmToMap(vmId: String, vm: ActorRef) = {
    VMs.put(vmId, vm)
  }

  private def createVM(specification: VirtualMachineSpecification,
                       hypervisor: Hypervisor) = {
    val id = Random.nextString(32)
    val ref = actorSystem.actorOf(Props(
        new VirtualMachineActor(
          specification.cpu, specification.ram, specification.disk, hypervisors.get(hypervisor.id).orNull)),
      "/vm/" + id)
    if (actors != null) {
      actors.put(id, ref)
    }

    val vm = VM(id, VMState.CREATED.toString, specification.cpu, specification.ram, specification.disk,
      specification.userId, hypervisor.id, specification.cpu, specification.ram, specification.disk)
    vmRepository.save(vm)
    VMs.put(id, ref)
    ref
  }


  def selectVM(userId: String, specification: TaskSpecification): ActorRef = {
    // using MaxMin strategy choose resource that is most relevant for the task,
    // then choose vm (from user's vms) that has the least of it amongst those which can execute the task and not be overloaded
    ???
  }

  def migrateMostRelevantMachineIfPossible(hipervisor: ActorRef): Unit = {
    // choose VM that uses most of overprovisioned resource and check if migration is possible.
    // If so, select hipervisor by MaxMin strategy and migrate machine
    ???
  }

  def migrateAllMachinesIfPossible(hipervisor: ActorRef): Unit = {
    // try migrating all machines to other hipervisors by using MaxMin strategy
    ???
  }

}
