package pl.rpw.core.vm.message

import akka.actor.ActorRef

case class MigrationMessage(val hipervisor: ActorRef)
