package pl.rpw.core.global

import pl.rpw.core.ResourceType
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository}

object HypervisorSelector {
  def selectMostExploitedResource(allHypervisors: Seq[Hypervisor]) : ResourceType.Value = {
    val availableCpu = allHypervisors.map(_.freeCpu).reduce(Integer.sum)
    val availableRam = allHypervisors.map(_.freeRam).reduce(Integer.sum)
    val availableDisk = allHypervisors.map(_.freeDisk).reduce(Integer.sum)

    val cpu = allHypervisors.map(_.cpu).reduce(Integer.sum)
    val ram = allHypervisors.map(_.ram).reduce(Integer.sum)
    val disk = allHypervisors.map(_.disk).reduce(Integer.sum)

    val cpuExploitation = (cpu - availableCpu) / cpu
    val ramExploitation = (ram - availableRam) / ram
    val diskExploitation = (disk - availableDisk) / disk

    if (cpuExploitation >= ramExploitation && cpuExploitation >= diskExploitation) {
      ResourceType.CPU
    } else if (ramExploitation >= cpuExploitation && ramExploitation >= diskExploitation) {
      ResourceType.RAM
    } else {
      ResourceType.DISK_SPACE
    }
  }

  def selectMostRelevantResource(specification: VirtualMachineSpecification, allHypervisors: Seq[Hypervisor]) : ResourceType.Value = {
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

  def getOrderFunction(resource: ResourceType.Value): Function2[Hypervisor, Hypervisor, Boolean] = {
    if (resource == ResourceType.CPU) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeCpu(hypervisor1, hypervisor2)
    } else if (resource == ResourceType.RAM) {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeRam(hypervisor1, hypervisor2)
    } else {
      (hypervisor1: Hypervisor, hypervisor2: Hypervisor) => compareFreeDisk(hypervisor1, hypervisor2)
    }
  }

  def selectHypervisorByMaxMin(selectedHypervisors: Seq[Hypervisor],
                               specification: VirtualMachineSpecification): Hypervisor = {
    val allHypervisors = HypervisorRepository.findAll()
    val resource = selectMostRelevantResource(specification, allHypervisors)
    val orderFunction = getOrderFunction(resource)
    selectedHypervisors.sortWith(orderFunction).head
  }

  def selectHypervisor(specification: VirtualMachineSpecification): Hypervisor = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those
    // which can run the vm and not be overloaded
    val activeHypervisors = HypervisorRepository.findActiveWithEnoughFreeResources(
      specification.cpu, specification.ram, specification.disk)
    if (activeHypervisors.isEmpty) {
      val idleHypervisors = HypervisorRepository.findIdleWithEnoughFreeResources(
        specification.cpu, specification.ram, specification.disk)
      if (idleHypervisors.nonEmpty) {
        selectHypervisorByMaxMin(idleHypervisors, specification)
      } else {
        null
      }
    } else {
      selectHypervisorByMaxMin(activeHypervisors, specification)
    }
  }

  def selectDestinationHypervisorForMigration(specification: VirtualMachineSpecification, sourceHypervisor: Hypervisor): Hypervisor = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those
    // which can run the vm and not be overloaded
    val activeHypervisors = HypervisorRepository.findActiveWithEnoughFreeResourcesAndIdNot(
      specification.cpu, specification.ram, specification.disk, sourceHypervisor.id)
    if (activeHypervisors.isEmpty) {
      val idleHypervisors = HypervisorRepository.findIdleWithEnoughFreeResourcesAndIdNot(
        specification.cpu, specification.ram, specification.disk, sourceHypervisor.id)
      if (idleHypervisors.nonEmpty) {
        selectHypervisorByMaxMin(idleHypervisors, specification)
      } else {
        null
      }
    } else {
      selectHypervisorByMaxMin(activeHypervisors, specification)
    }
  }

  def selectDestinationHypervisorFrom(specification: VirtualMachineSpecification,
                                      hypervisors: Seq[Hypervisor],
                                      mostExploitedResource: ResourceType.Value): Hypervisor = {
    // using MaxMin strategy choose resource that is most relevant for the vm,
    // then choose hipervisor (physical machine) that has the least of it amongst those
    // which can run the vm and not be overloaded
    val candidates = hypervisors
      .filter(_.hasEnoughResources(specification))
      .sortWith(getOrderFunction(mostExploitedResource))
      .reverse

    candidates.headOption.orNull
  }

}
