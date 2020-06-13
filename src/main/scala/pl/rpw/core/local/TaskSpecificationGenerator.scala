package pl.rpw.core.local

import akka.actor.{ActorRef, ActorSystem}
import pl.rpw.core.Utils
import pl.rpw.core.local.message.TaskGenerationRequestMessage

import scala.concurrent.duration.Duration

class TaskSpecificationGenerator(generationPeriod: Duration,
                                 actorSystem: ActorSystem,
                                 id: String) extends Runnable {
  override def run(): Unit = {
    println(s"($id) starts requesting for tasks...")
    while(true) {
      try {
        val localUtilityActor = Utils.getActorRef(actorSystem, id)
        println(s"Sending task to $localUtilityActor")
        Thread.sleep(generationPeriod.toMillis)
        localUtilityActor ! TaskGenerationRequestMessage()
      } catch {
        case exception: Throwable =>
      }
    }
  }
}
