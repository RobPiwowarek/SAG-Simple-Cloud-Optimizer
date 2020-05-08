package pl.rpw.core

import akka.actor.Actor

class HelloActor extends Actor {
  def receive = {
    case "hello" =>
      println("hello back at you")
      sender() ! "hello back at you"
    case "me liek cookiez" =>
      println("om nom nom nom")
      sender() ! "om nom nom nom"
    case _ =>
      println("huh?")
      sender() ! "huh?"
  }
}
