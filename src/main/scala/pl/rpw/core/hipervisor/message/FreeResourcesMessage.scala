package pl.rpw.core.hipervisor.message

import akka.actor.ActorRef

case class FreeResourcesMessage(val vitrualMachine: ActorRef)
