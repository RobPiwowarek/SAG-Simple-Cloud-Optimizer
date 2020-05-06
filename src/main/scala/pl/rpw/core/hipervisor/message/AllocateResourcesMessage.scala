package pl.rpw.core.hipervisor.message

import akka.actor.ActorRef

case class AllocateResourcesMessage(val vitrualMachine: ActorRef) {
}
