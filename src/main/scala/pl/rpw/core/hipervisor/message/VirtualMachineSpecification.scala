package pl.rpw.core.hipervisor.message

import pl.rpw.core.persistance.vm.VM

class VirtualMachineSpecification(val cpu: Int,
                                  val ram: Int,
                                  val disk: Int) {

  def this(vm: VM) = {
    this(vm.cpu, vm.ram, vm.disk)
  }

  override def toString: String =
    s"""VirtualMachineSpecification cpu = $cpu ram = $ram disk = $disk""".stripMargin
}
