package pl.rpw.core.persistance.hypervisor

import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.{Consts, ResourceType}

final case class Hypervisor(id: String,
                            var state: String,
                            cpu: Int,
                            ram: Int,
                            disk: Int,
                            var freeCpu: Int,
                            var freeRam: Int,
                            var freeDisk: Int) {

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

  def currentUsage = {
    val cpuUsage: Double = (cpu - freeCpu).toDouble / cpu
    val memoryUsage: Double = (ram - freeRam).toDouble / ram
    val diskUsage: Double = (disk - freeDisk).toDouble / disk

    s"cpu = $cpuUsage, ram = $memoryUsage, disk = $diskUsage"
  }

  def isUnderprovisioning: Boolean = {
    val cpuUsage: Double = (cpu - freeCpu).toDouble / cpu
    val memoryUsage: Double = (ram - freeRam).toDouble / ram
    val diskUsage: Double = (disk - freeDisk).toDouble / disk

    cpuUsage < Consts.underprovisioningThreshold ||
      memoryUsage < Consts.underprovisioningThreshold ||
      diskUsage < Consts.underprovisioningThreshold
  }

  def isOverprovisioning: Boolean = {
    val cpuUsage: Double = (cpu - freeCpu).toDouble / cpu
    val memoryUsage: Double = (ram - freeRam).toDouble / ram
    val diskUsage: Double = (disk - freeDisk).toDouble / disk

    cpuUsage > Consts.overprovisioningThreshold ||
      memoryUsage > Consts.overprovisioningThreshold ||
      diskUsage > Consts.overprovisioningThreshold
  }

  def hasEnoughResources(specification: VirtualMachineSpecification): Boolean = {
    this.freeCpu >= specification.cpu &
      this.freeRam >= specification.ram &
      this.freeDisk >= specification.disk
  }

  def hasUsedResources: Boolean = {
    this.freeCpu != this.cpu || this.freeRam != this.ram || this.freeDisk != this.disk
  }

  override def toString: String =
    s"""Hypervisor id = $id state = $state cpu = $cpu ram = $ram disk = $disk freeCpu = $freeCpu freeRam = $freeRam freeDisk = $freeDisk """
      .stripMargin
}
