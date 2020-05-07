package pl.rpw.core.hipervisor.message

import akka.actor.ActorRef

final case class AttachVMMessage(val virtualMachine: ActorRef,
                                 val machineSpecification: VirtualMachineSpecification)
