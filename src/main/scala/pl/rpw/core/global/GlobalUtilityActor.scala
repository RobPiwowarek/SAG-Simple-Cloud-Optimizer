package pl.rpw.core.global

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import pl.rpw.core.ResourceType
import pl.rpw.core.global.message.{OverprovisioningMessage, TaskFinishedMessage, TaskRequestMessage, UnderprovisioningMessage, VirtualMachineRequestMassage}
import pl.rpw.core.hipervisor.message.{AttachVMMessage, VirtualMachineSpecification}
import pl.rpw.core.vm.VirtualMachineActor
import pl.rpw.core.vm.message.{TaskMessage, TaskSpecification}

import scala.collection.mutable

class GlobalUtilityActor(val hypervisor: mutable.HashSet[ActorRef])
                        (implicit val actorSystem: ActorSystem) extends Actor {
  private var usersVMs = Map[String, List[ActorRef]]()

  override def receive: Receive = {
    case VirtualMachineRequestMassage(userId, specification) =>
      val hypervisor = selectHipervisor(specification)
      val vm = createVM(specification, hypervisor)

      hypervisor ! AttachVMMessage(vm, specification)
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

  private def addVmToMap(userId: String,
                         vm: ActorRef) = {
    val vmList = usersVMs(userId) :+ vm
    usersVMs = usersVMs ++ Map(userId -> vmList)
  }

  private def createVM(specification: VirtualMachineSpecification,
                       hypervisor: ActorRef) = {
    actorSystem.actorOf(Props(
      new VirtualMachineActor(
        specification.resources(ResourceType.CPU),
        specification.resources(ResourceType.MEMORY),
        specification.resources(ResourceType.DISK_SPACE),
        hypervisor
      )))
  }

  def selectHipervisor(specification: VirtualMachineSpecification): ActorRef = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those which can run the vm and not be overloaded
    ???
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
