package pl.rpw.core

import akka.actor.{ActorRef, ActorSystem, Props}
import pl.rpw.core.global.GlobalUtilityActor
import pl.rpw.core.hipervisor.HypervisorActor
import pl.rpw.core.input.InputThread
import pl.rpw.core.persistance.hypervisor.{Hypervisor, HypervisorRepository}
import pl.rpw.core.persistance.vm.VMRepository

import scala.collection.mutable

object MainApp extends App {
  VMRepository.setup()
  HypervisorRepository.setup()
  HypervisorRepository.findAll().foreach(_ => println)

  val system = ActorSystem("SAG-System")

  val hv1 = Hypervisor(id = "hv-1", state = "IDLE", cpu = 3, ram = 3, disk = 3, freeCpu = 3, freeRam = 3, freeDisk = 3)
  val hv2 = Hypervisor(id = "hv-2", state = "IDLE", cpu = 4, ram = 5, disk = 6, freeCpu = 4, freeRam = 5, freeDisk = 6)
  HypervisorRepository.insert(hv1)
  HypervisorRepository.insert(hv2)
  system.actorOf(
    Props(new HypervisorActor(3, 3, 3, "hv-1")), "hv-1")
  system.actorOf(
    Props(new HypervisorActor(4, 5, 6, "hv-2")), "hv-2")

  var actors = new mutable.HashMap[String, ActorRef]
  val globalUtilityActor = system.actorOf(
    Props(new GlobalUtilityActor(actors)), "GUA")

  globalUtilityActor ! "hi"

  val inputThread = new InputThread(system, actors)
  inputThread.run()

  DBInitializer.db.close()
}
