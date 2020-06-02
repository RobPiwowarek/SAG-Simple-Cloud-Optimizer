package pl.rpw.core.global

import pl.rpw.core.ResourceType
import pl.rpw.core.persistance.{Hypervisor, HypervisorRepository}
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification

object HypervisorSelector {
  def selectMostRelevantResource(specification: VirtualMachineSpecification,
                                 hypervisorRepository: HypervisorRepository) : ResourceType.Value = {
    val allHypervisors = hypervisorRepository.findAll()

    val availableCpu = allHypervisors.map(_.freeCpu).reduce(Integer.sum)
    val availableRam = allHypervisors.map(_.freeRam).reduce(Integer.sum)
    val availableDisk = allHypervisors.map(_.freeDisk).reduce(Integer.sum)

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

  def compareFreeCpu(hypervisor1: Hypervisor, hypervisor2: Hypervisor) : Boolean = {
    hypervisor1.freeCpu <= hypervisor2.freeCpu
  }

  def compareFreeRam(hypervisor1: Hypervisor, hypervisor2: Hypervisor) : Boolean = {
    hypervisor1.freeRam <= hypervisor2.freeRam
  }

  def compareFreeDisk(hypervisor1: Hypervisor, hypervisor2: Hypervisor) : Boolean = {
    hypervisor1.freeDisk <= hypervisor2.freeDisk
  }

  def selectHypervisorByMaxMin(selectedHypervisors: Seq[Hypervisor],
                               hypervisorRepository: HypervisorRepository,
                               specification: VirtualMachineSpecification): Hypervisor = {
    val resource = selectMostRelevantResource(specification, hypervisorRepository)
    val orderFunction = if (resource == ResourceType.CPU) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeCpu(hypervisor1, hypervisor2)
    } else if (resource == ResourceType.RAM) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeRam(hypervisor1, hypervisor2)
    } else {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeDisk(hypervisor1, hypervisor2)
    }
    selectedHypervisors.sortWith(orderFunction).head
  }

  def selectHypervisor(specification: VirtualMachineSpecification,
                       hypervisorRepository: HypervisorRepository): Hypervisor = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those
    // which can run the vm and not be overloaded
    val activeHypervisors = hypervisorRepository.findActiveWithEnoughFreeResources(
      specification.cpu, specification.ram, specification.disk)
    if (activeHypervisors.isEmpty) {
      val idleHypervisors = hypervisorRepository.findIdleWithEnoughFreeResources(
        specification.cpu, specification.ram, specification.disk)
      if (idleHypervisors.isEmpty) {
        // no suitable machine
        // exceptional situation
      }
      selectHypervisorByMaxMin(idleHypervisors, hypervisorRepository, specification)
    } else {
      selectHypervisorByMaxMin(activeHypervisors, hypervisorRepository, specification)
    }
  }
}
