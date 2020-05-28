package pl.rpw.core.global.message

import akka.actor.ActorRef

case class OverprovisioningMessage(val hipervisor: ActorRef)
