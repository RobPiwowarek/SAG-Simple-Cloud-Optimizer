package pl.rpw.core.global.message

import akka.actor.ActorRef

case class UnderprovisioningMessage(val hipervisor: ActorRef)
