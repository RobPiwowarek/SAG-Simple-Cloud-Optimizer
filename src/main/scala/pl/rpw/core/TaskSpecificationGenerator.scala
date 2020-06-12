package pl.rpw.core

import akka.actor.ActorRef

import scala.concurrent.duration.Duration

class TaskSpecificationGenerator(generationPeriod: Duration,
                                 localAgent: ) extends Runnable {
  override def run(): Unit = {
    agent !
  }
}
