package pl.rpw.core.global

import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.Utils.{calculateCpuImportance, calculateDiskImportance, calculateRamImportance}
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository}
import pl.rpw.core.{Consts, ResourceType, Utils}

object HypervisorSelector extends LazyLogging {
  def selectMostExploitedResource(allHypervisors: Seq[Hypervisor]): ResourceType.Value = {
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

  def selectMostRelevantResource(specification: VirtualMachineSpecification,
                                 allHypervisors: Seq[Hypervisor]): ResourceType.Value = {
    val cpuImportance = calculateCpuImportance(specification, allHypervisors)
    val ramImportance = calculateRamImportance(specification, allHypervisors)
    val diskImportance = calculateDiskImportance(specification, allHypervisors)

    if (cpuImportance >= ramImportance && cpuImportance >= diskImportance) {
      ResourceType.CPU
    } else if (ramImportance >= cpuImportance && ramImportance >= diskImportance) {
      ResourceType.RAM
    } else {
      ResourceType.DISK_SPACE
    }
  }

  def selectHypervisorByMaxMin(selectedHypervisors: Seq[Hypervisor],
                               specification: VirtualMachineSpecification): Hypervisor = {
    val allHypervisors = HypervisorRepository.findAll()
    val resource = selectMostRelevantResource(specification, allHypervisors)
    val orderFunction = Utils.getHypervisorOrderingFunction(resource)
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
        logger.info("Could not find hypervisor for the following specification: " + specification.toString)
        Consts.EmptyHypervisor
      }
    } else {
      selectHypervisorByMaxMin(activeHypervisors, specification)
    }
  }

  def selectDestinationHypervisorForMigration(specification: VirtualMachineSpecification,
                                              sourceHypervisor: Hypervisor): Hypervisor = {
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
        Consts.EmptyHypervisor
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
    hypervisors
      .filter(_.hasEnoughResources(specification))
      .sortWith(Utils.getHypervisorOrderingFunction(mostExploitedResource))
      .reverse
      .headOption match {
      case Some(candidate) => candidate
      case None => Consts.EmptyHypervisor
    }
  }

}
