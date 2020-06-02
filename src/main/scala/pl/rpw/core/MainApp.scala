package pl.rpw.core

import akka.actor.{ActorRef, ActorSystem, Props}
import pl.rpw.core.global.GlobalUtilityActor
import pl.rpw.core.persistance.HypervisorRepository
import pl.rpw.core.input.InputThread

import scala.collection.mutable


object MainApp extends App {
  val hypervisorRepository = new HypervisorRepository()

  hypervisorRepository.setup()
  hypervisorRepository.findAll().foreach(_ => println)

  val system = ActorSystem("SAG-System")

  var actors = new mutable.HashMap[String, ActorRef]
  val globalUtilityActor = system.actorOf(
    Props(new GlobalUtilityActor(actors)), "GUA")

  globalUtilityActor ! "hi"

  // default Actor constructor
  val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")
  helloActor ! "hello"
  helloActor ! "buenos dias"
  helloActor ! "I never loved you"
  helloActor ! "me liek cookiez"


  val inputThread = new InputThread(system, actors)
  inputThread.run()

}
