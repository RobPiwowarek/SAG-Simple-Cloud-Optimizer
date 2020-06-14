package pl.rpw.core.local

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.Utils
import pl.rpw.core.local.message.TaskGenerationRequestMessage

import scala.concurrent.duration.Duration

class TaskSpecificationGenerationScheduler(generationPeriod: Duration,
                                           actorSystem: ActorSystem,
                                           id: String) extends Runnable with LazyLogging {
  override def run(): Unit = {
    logger.info(s"($id) starts requesting for tasks...")
    while(true) {
      try {
        val localUtilityActor = Utils.getActorRef(actorSystem, id)
        logger.info(s"Sending task to $localUtilityActor")
        Thread.sleep(generationPeriod.toMillis)
        localUtilityActor ! TaskGenerationRequestMessage()
      } catch {
        case exception: Throwable =>
      }
    }
  }
}
