package pl.rpw.core

import akka.actor.ActorRef
import pl.rpw.core.local.message.TaskGenerationRequestMessage

import scala.concurrent.duration.Duration

class TaskSpecificationGenerator(generationPeriod: Duration,
                                 localUtilityActor: ActorRef) extends Runnable {
  override def run(): Unit = {
    while(true) {
      this.wait(generationPeriod.toMillis)
      localUtilityActor ! TaskGenerationRequestMessage()
    }
  }
}
