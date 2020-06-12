package pl.rpw.core.local

import java.util.UUID

import akka.actor.Actor
import pl.rpw.core.Utils
import pl.rpw.core.global.message.{TaskRequestMessage, VirtualMachineRequestMassage}
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.local.message.{CreateVMMessage, TaskGenerationRequestMessage}
import pl.rpw.core.vm.message.TaskSpecification

import scala.util.Random

class LocalUtilityActor(val id: String,
                        val cpuAmplitude: Double,
                        val ramAmplitude: Double,
                        val diskAmplitude: Double,
                        val timeAmplitude: Double,
                        val taskGenerationPeriod: Int
                       ) extends Actor {

  val actorSystem = context.system

  def adjustSpecification(specification: VirtualMachineSpecification): TaskSpecification = {

  }

  override def receive: Receive = {
    case CreateVMMessage(specification) =>
      println(s"""Sending $specification to Global Utility Actor""")
      val globalRef = Utils.globalUtility(actorSystem)
      val adjustedSpecification = adjustSpecification(specification)
      globalRef ! VirtualMachineRequestMassage(id, specification)
    case TaskGenerationRequestMessage =>
      generateTask()
    case "start" =>

  }

  private def generateTask() = {
    val globalRef = Utils.globalUtility(actorSystem)
    globalRef ! TaskRequestMessage(new TaskSpecification(UUID.randomUUID().toString, id, , , , ))
  }

}
