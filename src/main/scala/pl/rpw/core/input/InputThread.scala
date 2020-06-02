package pl.rpw.core.input

import akka.actor.{ActorRef, ActorSystem, Props}
import pl.rpw.core.vm.VirtualMachineActor

import scala.collection.mutable
import scala.io.StdIn

class InputThread(actorSystem: ActorSystem, actors: mutable.Map[String, ActorRef]) extends Thread {

  def createVM(data: String): ActorRef = {
    val specification = data.split("/")
    actorSystem.actorOf(Props(
      new VirtualMachineActor(
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2)),
        null
      )))
  }

  def createActor(actorType: String, id: String, data: String): ActorRef = {
    val ref = actorType match {
      case "VM" => createVM(data)
    }
    actors.put(id, ref)
    ref
  }

  def startAgent(data: String): Unit = {
    val id = data.split(" ")(0)
    val actorType = data.split(" ")(1)

    val ref = actors.getOrElse(id, createActor(actorType, id, data.split(" ")(2)))

    println("started: " + ref)
  }

  def stopAgent(data: String): Unit = {
    actors.get(data)
      .fold {
        println("No such agent")
      } { ref => {
        println("Stopping: " + ref)
        actors.remove(data)
        actorSystem.stop(ref)
      }}
  }

  override def run(): Unit = {
    while (true) {
      val line = StdIn.readLine().split(":")
      val command = line(0)
      val data = line(1)

      command match {
        case "start" => {
          startAgent(data)
        }
        case "stop" => {
          stopAgent(data)
        }
        case "task" => print("task", data)
      }
    }
  }
}
