package pl.rpw.core

import akka.actor.{ActorSystem, Props}

object MainApp extends App {
  val system = ActorSystem("HelloSystem")
  // default Actor constructor
  val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")
  helloActor ! "hello"
  helloActor ! "buenos dias"
  helloActor ! "I never loved you"
  helloActor ! "me liek cookiez"

}
