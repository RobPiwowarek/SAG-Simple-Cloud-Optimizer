package pl.rpw.core.hipervisor.message

final case class AttachVMMessage(val vmId: String,
                                 val machineSpecification: VirtualMachineSpecification)
