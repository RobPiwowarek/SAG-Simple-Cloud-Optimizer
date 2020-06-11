package pl.rpw.core.input

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import pl.rpw.core.global.message.VirtualMachineRequestMassage
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.persistance.hypervisor.HypervisorRepository
import pl.rpw.core.persistance.vm.VMRepository
import pl.rpw.core.vm.VirtualMachineActor

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.io.StdIn
import scala.util.{Failure, Success}

class InputThread(actorSystem: ActorSystem, actors: mutable.Map[String, ActorRef]) extends Thread {

  def createVM(id:String, data: String): ActorRef = {
    val specification = data.split("/")
    actorSystem.actorOf(Props(
      new VirtualMachineActor(
        id,
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2))
      )))
  }

  def createActor(actorType: String, id: String, data: String): ActorRef = {
    val ref = actorType match {
      case "VM" | "vm" => createVM(id, data)
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
      }
      }
  }

  def requestVM(specification: Array[String]): Unit = {
    implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
    implicit val ec = scala.concurrent.ExecutionContext.global
    actorSystem.actorSelection("user/GUA").resolveOne().onComplete{
      case Success(ref) => {
        val vmSpecification = new VirtualMachineSpecification(
          Integer.parseInt(specification(0)),
          Integer.parseInt(specification(1)),
          Integer.parseInt(specification(2)))
        ref ! VirtualMachineRequestMassage("user", vmSpecification)
      }
      case Failure(exception) => println("Cannot find GUA")
    }
  }

  def requestAgent(data: String): Unit = {
    val actorType = data.split(" ")(0)
    val specification = data.split(" ")(1).split("/")
    actorType match {
      case "vm" => requestVM(specification)
      case _ => println("no such type")
    }
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
        case "request" => {
          requestAgent(data)
        }
        case "task" => print("task", data)
        case "print" => {
          HypervisorRepository.findAll()
          VMRepository.findAll()
        }
      }
    }
  }
}
