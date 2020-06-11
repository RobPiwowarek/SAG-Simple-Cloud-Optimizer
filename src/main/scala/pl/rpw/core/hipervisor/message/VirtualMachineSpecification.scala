package pl.rpw.core.hipervisor.message

class VirtualMachineSpecification(val cpu: Int, val ram: Int, val disk: Int) {
  override def toString: String = {
    "VirtualMachineSpecification(cpu: " + cpu +
      ", ram: " + ram +
      ", disk: " + disk +")"
  }
}
