package pl.rpw.core.global

import pl.rpw.core.persistance.hypervisor.Hypervisor
import pl.rpw.core.persistance.task.TaskSpecification
import pl.rpw.core.persistance.vm.{VM, VMRepository}
import pl.rpw.core.{Consts, ResourceType, Utils}

object VMSelector {
  def selectMostRelevantResource(specification: TaskSpecification): ResourceType.Value = {
    val allVMs = VMRepository.findAll()

    val cpuImportance = Utils.calculateCpuImportance(specification, allVMs)
    val ramImportance = Utils.calculateRamImportance(specification, allVMs)
    val diskImportance = Utils.calculateDiskImportance(specification, allVMs)

    if (cpuImportance >= ramImportance && cpuImportance >= diskImportance) {
      ResourceType.CPU
    } else if (ramImportance >= cpuImportance && ramImportance >= diskImportance) {
      ResourceType.RAM
    } else {
      ResourceType.DISK_SPACE
    }
  }

  def selectVMforTaskByMaxMin(selectedVMs: Seq[VM],
                              specification: TaskSpecification): VM = {
    val resource = selectMostRelevantResource(specification)
    val orderFunction = Utils.getVmOrderingFunctionForTask(resource)
    selectedVMs.sortWith(orderFunction).head
  }

  def selectVMForMigrationByMaxMin(selectedVMs: Seq[VM],
                                   resource: ResourceType.Value): VM = {
    val orderFunction = Utils.getVmOrderingFunctionForMigration(resource)
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
      if (idleVMs.nonEmpty) {
        selectVMforTaskByMaxMin(idleVMs, specification)
      } else {
        Consts.EmptyVM
      }
    } else {
      selectVMforTaskByMaxMin(activeVMs, specification)
    }
  }

  def selectVMToMigrate(hypervisor: Hypervisor): VM = {
    // using MaxMin strategy choose resource that is most relevant for the hypervisor,
    // then choose VM that has the most of it
    val mostRelevantResource = hypervisor.selectMostExploitedResource()
    val activeVMs = VMRepository.findActiveByHypervisor(hypervisor.id)
    if (activeVMs.isEmpty) {
      val idleVMs = VMRepository.findIdleByHypervisor(hypervisor.id)
      if (idleVMs.nonEmpty) {
        selectVMForMigrationByMaxMin(idleVMs, mostRelevantResource)
      } else {
        Consts.EmptyVM
      }
    } else {
      selectVMForMigrationByMaxMin(activeVMs, mostRelevantResource)
    }
  }
}
