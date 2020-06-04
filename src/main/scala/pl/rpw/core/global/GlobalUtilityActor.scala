package pl.rpw.core.global

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import pl.rpw.core.global.message._
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.TaskMessage

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
      migrateMostRelevantMachineIfPossible(hypervisor)

    case UnderprovisioningMessage(hypervisor) =>
      migrateAllMachinesIfPossible(hypervisor)

    case TaskFinishedMessage(_) =>
    //respond to local agent about task execution
  }

  private def createVM(userId: String,
                       specification: VirtualMachineSpecification,
                       hypervisor: Hypervisor) = {
    val id = Random.alphanumeric.take(32).mkString("")
    val ref = actorSystem.actorOf(Props(
      new VirtualMachineActor(
        specification.cpu, specification.ram, specification.disk, hypervisors.get(hypervisor.id).orNull)), id)
    if (actors != null) {
      actors.put(id, ref)
    }

    val vm = VM(id, VMState.CREATED.toString, specification.cpu, specification.ram, specification.disk,
      userId, hypervisor.id, specification.cpu, specification.ram, specification.disk)
    VMRepository.insert(vm)
    VMs.put(id, ref)
    vm
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
