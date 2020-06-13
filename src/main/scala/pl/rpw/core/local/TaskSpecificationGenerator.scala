package pl.rpw.core.local

import akka.actor.ActorRef
import pl.rpw.core.local.message.TaskGenerationRequestMessage

import scala.concurrent.duration.Duration

class TaskSpecificationGenerator(generationPeriod: Duration,
                                 localUtilityActor: ActorRef,
                                 id: String) extends Runnable {
  override def run(): Unit = {
    println(s"($id) starts requesting for tasks...")
    while(true) {
      this.wait(generationPeriod.toMillis)
      localUtilityActor ! TaskGenerationRequestMessage()
    }
  }
}
