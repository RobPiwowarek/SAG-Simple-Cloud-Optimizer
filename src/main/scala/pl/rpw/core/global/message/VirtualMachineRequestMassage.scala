package pl.rpw.core.global.message

import pl.rpw.core.hipervisor.message.VirtualMachineSpecification

case class VirtualMachineRequestMassage(val userId: String, val specification: VirtualMachineSpecification)
