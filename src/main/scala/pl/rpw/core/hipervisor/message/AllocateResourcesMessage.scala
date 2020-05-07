package pl.rpw.core.hipervisor.message

import akka.actor.ActorRef

final case class AllocateResourcesMessage(virtualMachine: ActorRef) {
}
