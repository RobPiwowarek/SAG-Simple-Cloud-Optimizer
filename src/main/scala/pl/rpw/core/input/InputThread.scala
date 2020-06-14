package pl.rpw.core.input

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import pl.rpw.core.MainApp.system
import pl.rpw.core.global.GlobalUtilityActor
import pl.rpw.core.global.message.{TaskRequestMessage, VirtualMachineRequestMassage}
import pl.rpw.core.hipervisor.HypervisorActor
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.local.message.CreateVMMessage
import pl.rpw.core.local.{LocalUtilityActor, TaskSpecificationGenerationScheduler}
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository}
import pl.rpw.core.persistance.task.{TaskSpecification, TaskSpecificationsRepository}
import pl.rpw.core.persistance.vm.VMRepository
import pl.rpw.core.vm.VirtualMachineActor

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.{Failure, Success}

class InputThread(actorSystem: ActorSystem,
                  actors: mutable.Map[String, ActorRef]) extends Thread {

  def createVM(id: String,
               data: String): ActorRef = {
    val specification = data.split("/")
    actorSystem.actorOf(Props(
      new VirtualMachineActor(
        id,
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2))
      )))
  }

  def createLocalAgent(id: String, data: String) = {
    val specification = data.split("/")
    val ref = actorSystem.actorOf(Props(
      new LocalUtilityActor(
        id,
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2)),
        Integer.parseInt(specification(3)),
        Integer.parseInt(specification(4))
      )
    ), id)
    new Thread(
      new TaskSpecificationGenerationScheduler(
        FiniteDuration(Integer.parseInt(specification(4)), TimeUnit.SECONDS), actorSystem, id)
    ).start()
    actors.put(id, ref)
  }

  def createHypervisor(id: String, data: String) = {
    val specification = data.split("/")
    val hv1 = Hypervisor(id = id, state = "IDLE",
      cpu = Integer.parseInt(specification(0)),
      ram = Integer.parseInt(specification(1)),
      disk = Integer.parseInt(specification(2)),
      freeCpu = Integer.parseInt(specification(0)),
      freeRam = Integer.parseInt(specification(1)),
      freeDisk = Integer.parseInt(specification(2))
    )
    HypervisorRepository.insert(hv1)
    val ref = system.actorOf(
      Props(new HypervisorActor(
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2)),
        id)),
      id)
    actors.put(id, ref)
  }

  def runVmFlowForLUA(data: String) = {
    val specification = data.split("/")
    val lua = specification(3)
    val vmSpecification = new VirtualMachineSpecification(
        Integer.parseInt(specification(0)),
        Integer.parseInt(specification(1)),
        Integer.parseInt(specification(2))
      )

    implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
    implicit val ec = scala.concurrent.ExecutionContext.global
    actorSystem.actorSelection(s"user/$lua").resolveOne().onComplete {
      case Success(ref) => {
        ref ! CreateVMMessage(vmSpecification)
      }
      case Failure(exception) => println("Cannot find GUA")
    }
    null
  }

  def createActor(actorType: String,
                  data: String) {
    val splitData = data.split("-")
    actorType match {
      case "GUA" | "gua" => createGlobalAgent
      case "LUA" | "lua" => createLocalAgent(splitData(0), splitData(1))
      case "HV" | "hv" => createHypervisor(splitData(0), splitData(1))
      case "VM" | "vm" => runVmFlowForLUA(data)
    }
  }

  private def createGlobalAgent = {
    val ref = actorSystem.actorOf(Props(
      new GlobalUtilityActor(actors)), "GUA")
    actors.put("gua", ref)
  }

  def startAgent(data: String): Unit = {
    val actorType = data.split(" ")(0)

    val ref = createActor(actorType, data.split(" ")(1))

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
    actorSystem.actorSelection("user/GUA").resolveOne().onComplete {
      case Success(ref) => {
        val vmSpecification = new VirtualMachineSpecification(
          Integer.parseInt(specification(0)),
          Integer.parseInt(specification(1)),
          Integer.parseInt(specification(2)))
        ref ! VirtualMachineRequestMassage(specification(3), vmSpecification)
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

  def requestTask(data: String): Unit = {
    val specification = data.split("/")
    implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
    implicit val ec = scala.concurrent.ExecutionContext.global
    actorSystem.actorSelection("user/GUA").resolveOne().onComplete {
      case Success(ref) => {
        val taskSpecification: TaskSpecification = TaskSpecification(
          specification(0),
          specification(1),
          Integer.parseInt(specification(2)),
          Integer.parseInt(specification(3)),
          Integer.parseInt(specification(4)),
          Integer.parseInt(specification(5))
        )
        ref ! TaskRequestMessage(taskSpecification)
      }
      case Failure(exception) => println(s"Cannot find GUA: ${exception.getMessage}")
    }
  }

  override def run(): Unit = {
    while (true) {
      try {
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
          case "task" => {
            requestTask(data)
          }
          case "print" => {
            HypervisorRepository.findAll().foreach(println)
            VMRepository.findAll().foreach(println)
            TaskSpecificationsRepository.findAll().foreach(println)
            actors.foreach{case (id, ref) => println(s"actor: $id $ref")}
          }
          case _ => println("Illegal command")
        }
      } catch {
        case exception: Throwable => println(exception)
      }
    }
  }
}
