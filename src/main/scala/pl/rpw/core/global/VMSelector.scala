package pl.rpw.core.global

import pl.rpw.core.ResourceType
import pl.rpw.core.global.HypervisorSelector.{compareFreeCpu, compareFreeDisk, compareFreeRam}
import pl.rpw.core.persistance.hypervisor.Hypervisor
import pl.rpw.core.persistance.vm.{VM, VMRepository}
import pl.rpw.core.vm.message.TaskSpecification

object VMSelector {
  def selectMostRelevantResource(specification: TaskSpecification): ResourceType.Value = {
    val allVMs = VMRepository.findAll()

    val availableCpu = allVMs.map(_.freeCpu).reduce(Integer.sum)
    val availableRam = allVMs.map(_.freeRam).reduce(Integer.sum)
    val availableDisk = allVMs.map(_.freeDisk).reduce(Integer.sum)

    val cpuImportance = specification.cpu / availableCpu
    val ramImportance = specification.ram / availableRam
    val diskImportance = specification.disk / availableDisk

    if (cpuImportance >= ramImportance && cpuImportance >= diskImportance) {
      ResourceType.CPU
    } else if (ramImportance >= cpuImportance && ramImportance >= diskImportance) {
      ResourceType.RAM
    } else {
      ResourceType.DISK_SPACE
    }
  }

  def compareFreeCpu(vm1: VM, vm2: VM): Boolean = {
    vm1.freeCpu <= vm2.freeCpu
  }

  def compareFreeRam(vm1: VM, vm2: VM): Boolean = {
    vm1.freeRam <= vm2.freeRam
  }

  def compareFreeDisk(vm1: VM, vm2: VM): Boolean = {
    vm1.freeDisk <= vm2.freeDisk
  }

  def getOrderFunction(resource: ResourceType.Value): Function2[VM, VM, Boolean] = {
    if (resource == ResourceType.CPU) {
      (vm1: VM, vm2: VM) => compareFreeCpu(vm1, vm2)
    } else if (resource == ResourceType.RAM) {
      (vm1: VM, vm2: VM) => compareFreeRam(vm1, vm2)
    } else {
      (vm1: VM, vm2: VM) => compareFreeDisk(vm1, vm2)
    }
  }

  def selectVMByMaxMin(selectedVMs: Seq[VM],
                       specification: TaskSpecification): VM = {
    val resource = selectMostRelevantResource(specification)
    val orderFunction = getOrderFunction(resource)
    selectedVMs.sortWith(orderFunction).head
  }

  def selectVMByMaxMin(selectedVMs: Seq[VM],
                       resource: ResourceType.Value): VM = {
    val orderFunction = if (resource == ResourceType.CPU) {
      (vm1: VM, vm2: VM) => compareFreeCpu(vm1, vm2)
    } else if (resource == ResourceType.RAM) {
      (vm1: VM, vm2: VM) => compareFreeRam(vm1, vm2)
    } else {
      (vm1: VM, vm2: VM) => compareFreeDisk(vm1, vm2)
    }
    selectedVMs.sortWith(orderFunction).head
  }

  def selectVM(userId: String, specification: TaskSpecification): VM = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those
    // which can run the vm and not be overloaded
    val activeVMs = VMRepository.findActiveByUserAndEnoughFreeResources(
      userId, specification.cpu, specification.ram, specification.disk)
    if (activeVMs.isEmpty) {
      val idleVMs = VMRepository.findIdleByUserAndEnoughFreeResources(
        userId, specification.cpu, specification.ram, specification.disk)
      if (idleVMs.isEmpty) {
        // no suitable machine
        // exceptional situation
      }
      selectVMByMaxMin(idleVMs, specification)
    } else {
      selectVMByMaxMin(activeVMs, specification)
    }
  }

  def selectVMToMigrate(hypervisor: Hypervisor): VM = {
    // using MaxMin strategy choose resource that is most relevant for the hypervisor,
    // then choose VM that has the most of it 
    val mostRelevantResource = hypervisor.selectMostExploitedResource()
    val activeVMs = VMRepository.findActiveByHypervisor(hypervisor.id)
    if (activeVMs.isEmpty) {
      val idleVMs = VMRepository.findIdleByHypervisor(hypervisor.id)
      if (idleVMs.isEmpty) {
        // no suitable machine
        // exceptional situation
      }
      selectVMByMaxMin(idleVMs, mostRelevantResource)
    } else {
      selectVMByMaxMin(activeVMs, mostRelevantResource)
    }
  }
}
