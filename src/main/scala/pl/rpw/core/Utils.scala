package pl.rpw.core

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.local.message.VmIsDeadMessage
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository, HypervisorState}
import pl.rpw.core.persistance.task.{TaskSpecification, TaskSpecificationsRepository}
import pl.rpw.core.persistance.vm.{VM, VMRepository, VMState}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}

object Utils {
  def getVmOrderingFunctionForMigration(resource: ResourceType.Value): (VM, VM) => Boolean = {
    if (resource == ResourceType.CPU) {
      (vm1: VM, vm2: VM) => compareCpu(vm1, vm2)
    } else if (resource == ResourceType.RAM) {
      (vm1: VM, vm2: VM) => compareRam(vm1, vm2)
    } else {
      (vm1: VM, vm2: VM) => compareDisk(vm1, vm2)
    }
  }

  def getVmOrderingFunctionForTask(resource: ResourceType.Value): (VM, VM) => Boolean = {
    if (resource == ResourceType.CPU) {
      (vm1: VM, vm2: VM) => compareFreeCpu(vm1, vm2)
    } else if (resource == ResourceType.RAM) {
      (vm1: VM, vm2: VM) => compareFreeRam(vm1, vm2)
    } else {
      (vm1: VM, vm2: VM) => compareFreeDisk(vm1, vm2)
    }
  }

  def getHypervisorOrderingFunction(resource: ResourceType.Value): Function2[Hypervisor, Hypervisor, Boolean] = {
    if (resource == ResourceType.CPU) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeCpu(hypervisor1, hypervisor2)
    } else if (resource == ResourceType.RAM) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeRam(hypervisor1, hypervisor2)
    } else {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeDisk(hypervisor1, hypervisor2)
    }
  }

  def calculateCpuImportance(specification: VirtualMachineSpecification,
                             allHypervisors: Seq[Hypervisor]) = {
    val availableCpu = allHypervisors.map(_.freeCpu).reduce(Integer.sum)
    specification.cpu / availableCpu
  }

  def calculateRamImportance(specification: VirtualMachineSpecification,
                             allHypervisors: Seq[Hypervisor]) = {
    val availableRam = allHypervisors.map(_.freeRam).reduce(Integer.sum)
    specification.ram / availableRam
  }

  def calculateDiskImportance(specification: VirtualMachineSpecification,
                              allHypervisors: Seq[Hypervisor]) = {
    val availableDisk = allHypervisors.map(_.freeDisk).reduce(Integer.sum)
    specification.disk / availableDisk
  }

  def calculateCpuImportance(specification: TaskSpecification,
                             allVms: Seq[VM]) = {
    val availableCpu = allVms.map(_.freeCpu).reduce(Integer.sum)
    specification.cpu / availableCpu
  }

  def calculateRamImportance(specification: TaskSpecification,
                             allVms: Seq[VM]) = {
    val availableRam = allVms.map(_.freeRam).reduce(Integer.sum)
    specification.ram / availableRam
  }

  def calculateDiskImportance(specification: TaskSpecification,
                              allVms: Seq[VM]) = {
    val availableDisk = allVms.map(_.freeDisk).reduce(Integer.sum)
    specification.disk / availableDisk
  }

  private def compareCpu(vm1: VM, vm2: VM): Boolean = {
    vm1.cpu <= vm2.cpu
  }

  private def compareRam(vm1: VM, vm2: VM): Boolean = {
    vm1.ram <= vm2.ram
  }

  private def compareDisk(vm1: VM, vm2: VM): Boolean = {
    vm1.disk <= vm2.disk
  }

  private def compareFreeCpu(vm1: VM, vm2: VM): Boolean = {
    vm1.freeCpu <= vm2.freeCpu
  }

  private def compareFreeRam(vm1: VM, vm2: VM): Boolean = {
    vm1.freeRam <= vm2.freeRam
  }

  private def compareFreeDisk(vm1: VM, vm2: VM): Boolean = {
    vm1.freeDisk <= vm2.freeDisk
  }

  private def compareFreeCpu(hypervisor1: Hypervisor, hypervisor2: Hypervisor): Boolean = {
    hypervisor1.freeCpu <= hypervisor2.freeCpu
  }

  private def compareFreeRam(hypervisor1: Hypervisor, hypervisor2: Hypervisor): Boolean = {
    hypervisor1.freeRam <= hypervisor2.freeRam
  }

  private def compareFreeDisk(hypervisor1: Hypervisor, hypervisor2: Hypervisor): Boolean = {
    hypervisor1.freeDisk <= hypervisor2.freeDisk
  }

  def globalUtility(actorSystem: ActorSystem) = {
    Await.result(actorSystem.actorSelection("user/GUA").resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
  }

  def getActorRef(actorSystem: ActorSystem, path: String) = {
    Await.result(actorSystem.actorSelection(s"user/$path").resolveOne(FiniteDuration(1, TimeUnit.SECONDS)), Duration.Inf)
  }

  def markAllMachinesAsDeadAdNotifyOwners(hypervisor: Hypervisor, actorSystem: ActorSystem): Unit = {
    VMRepository.findByHypervisor(hypervisor.id)
      .foreach(vm => {
        markMachineAsDeadAndNotifyOwner(vm, actorSystem)
      })
  }

  def markMachineAsDeadAndNotifyOwner(vm: VM, actorSystem: ActorSystem) = {
    vm.state = VMState.DEAD.toString
    VMRepository.update(vm)
    val tasks = TaskSpecificationsRepository
      .findByVm(vm.id)
      .map(_.taskId)
    tasks.foreach(TaskSpecificationsRepository.remove)
    try {
      val userRef = Utils.getActorRef(actorSystem, vm.user)
      userRef ! VmIsDeadMessage(vm.id, tasks)
    } catch {
      case exception: Throwable => println(s"Could not find actor for userId: ${vm.user}")
    }
    try {
      val hypervisorRef = Utils.getActorRef(actorSystem, vm.hypervisor.orNull)
      hypervisorRef ! VmIsDeadMessage(vm.id, tasks)
    } catch {
      case exception: Throwable => println(s"Could not find actor for hypervisor: ${vm.hypervisor.orNull}")
    }
  }

  def markHypervisorAsDeadRecursively(hypervisor: Hypervisor, actorSystem: ActorSystem) = {
    hypervisor.state = HypervisorState.DEAD.toString
    HypervisorRepository.update(hypervisor)
    markAllMachinesAsDeadAdNotifyOwners(hypervisor, actorSystem)
  }
}
