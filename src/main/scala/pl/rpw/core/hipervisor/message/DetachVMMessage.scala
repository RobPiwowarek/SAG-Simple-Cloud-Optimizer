package pl.rpw.core.hipervisor.message

import akka.actor.ActorRef

case class DetachVMMessage(val vitrualMachine: ActorRef)
