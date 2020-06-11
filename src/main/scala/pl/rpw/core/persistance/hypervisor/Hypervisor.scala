package pl.rpw.core.persistance.hypervisor

import pl.rpw.core.ResourceType
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification

case class Hypervisor(id: String, var state: String, cpu: Int, ram: Int, disk: Int, var freeCpu: Int, var freeRam: Int, var freeDisk: Int) {
  def selectMostExploitedResource(): ResourceType.Value = {
    val cpuExploitation = (this.cpu - this.freeCpu) / this.cpu
    val ramExploitation = (this.ram - this.freeRam) / this.ram
    val diskExploitation = (this.disk - this.freeDisk) / this.disk

    if (cpuExploitation >= ramExploitation && cpuExploitation >= diskExploitation) {
      ResourceType.CPU
    } else if (ramExploitation >= cpuExploitation && ramExploitation >= diskExploitation) {
      ResourceType.RAM
    } else {
      ResourceType.DISK_SPACE
    }
  }

  def hasEnoughResources(specification: VirtualMachineSpecification): Boolean = {
      this.freeCpu >= specification.cpu &
      this.freeRam >= specification.ram &
      this.freeDisk >= specification.disk
  }
}
