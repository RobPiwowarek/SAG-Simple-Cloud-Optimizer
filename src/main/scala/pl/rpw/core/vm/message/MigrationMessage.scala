package pl.rpw.core.vm.message

import akka.actor.ActorRef

final case class MigrationMessage(val hypervisor: ActorRef)
