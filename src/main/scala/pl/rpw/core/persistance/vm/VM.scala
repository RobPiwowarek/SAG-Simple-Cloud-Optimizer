package pl.rpw.core.persistance.vm

import pl.rpw.core.hipervisor.message.VirtualMachineSpecification

final case class VM(id: String,
                    var state: String,
                    cpu: Int,
                    ram: Int,
                    disk: Int,
                    user: String,
                    var hypervisor: Option[String],
                    var freeCpu: Int,
                    var freeRam: Int,
                    var freeDisk: Int) {

  def hasActivelyUsedResources: Boolean = {
    this.freeCpu != this.cpu || this.freeRam != this.ram || this.freeDisk != this.disk
  }

  def toSpecs = new VirtualMachineSpecification(this)

  override def toString: String = {
    s"""VM id = $id state = $state cpu = $cpu ram = $ram disk = $disk user = $user hypervisor $hypervisor freeCpu = $freeCpu freeRam = $freeRam freeDisk = $freeDisk """
      .stripMargin
  }
}
