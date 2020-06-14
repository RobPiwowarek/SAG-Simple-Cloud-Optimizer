package pl.rpw.core.local

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.rpw.core.Utils
import pl.rpw.core.local.message.TaskGenerationRequestMessage

import scala.concurrent.duration.Duration

class TaskSpecificationGenerationScheduler(generationPeriod: Duration,
                                           actorSystem: ActorSystem,
                                           id: String) extends Runnable with LazyLogging {
  override def run(): Unit = {
    logger.info(s"($id) starts requesting for tasks in 20s...")
    Thread.sleep(Duration(20, TimeUnit.SECONDS).toMillis)
    while (true) {
      Thread.sleep(generationPeriod.toMillis)
      val localUtilityActor = Utils.getActorRef(actorSystem, id)
      logger.info(s"Sending task to $localUtilityActor")
      localUtilityActor ! TaskGenerationRequestMessage()
    }
  }
}
