package pl.rpw.core.local.message

import pl.rpw.core.hipervisor.message.VirtualMachineSpecification

case class CreateVMMessage(val specification: VirtualMachineSpecification)
