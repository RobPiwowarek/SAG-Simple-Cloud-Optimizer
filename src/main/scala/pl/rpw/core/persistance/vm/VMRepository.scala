package pl.rpw.core.persistance.vm

import pl.rpw.core.DBInitializer
import pl.rpw.core.persistance.hypervisor.Hypervisor
import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object VMRepository {
  def findByHypervisor(hypervisor: String): Seq[VM] = {
    val query = tableQuery
      .filter(_.hypervisor === hypervisor)
    Await.result(db.run(query.result), Duration.Inf)
  }

  val tableQuery = TableQuery[VMs]
  implicit val ec = scala.concurrent.ExecutionContext.global
  val db = DBInitializer.db

  def setup(): Unit = {
    Await.result(db.run(tableQuery.schema.createIfNotExists), Duration.Inf)
  }

  def findAll(): Seq[VM] = {
    Await.result(db.run(tableQuery.result), Duration.Inf)
  }

  def findActiveByUserAndEnoughFreeResources(userId: String, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    findByUserAndStateAndEnoughResources(userId, VMState.ACTIVE, requiredCpu, requiredRam, requiredDisk)
  }

  def findIdleByUserAndEnoughFreeResources(userId: String, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    findByUserAndStateAndEnoughResources(userId, VMState.IDLE, requiredCpu, requiredRam, requiredDisk)
  }

  def findByUserAndStateAndEnoughResources(userId: String, state: VMState.Value, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    val query = tableQuery
      .filter(_.user === userId)
      .filter(_.freeCpu >= requiredCpu)
      .filter(_.freeRam >= requiredRam)
      .filter(_.freeDisk >= requiredDisk)
      .filter(_.state === state.toString)
    Await.result(db.run(query.result), Duration.Inf)

  }

  def findActiveByHypervisor(hypervisor: String): Seq[VM] = {
    val query = tableQuery
      .filter(_.hypervisor === hypervisor)
      .filter(_.state === VMState.ACTIVE.toString)
    Await.result(db.run(query.result), Duration.Inf)
  }

  def findIdleByHypervisor(hypervisor: String): Seq[VM] = {
    val query = tableQuery
      .filter(_.hypervisor === hypervisor)
      .filter(_.state ===VMState.IDLE.toString)
    Await.result(db.run(query.result), Duration.Inf)
  }

  def findById(id: String): VM = {
    val query = tableQuery
      .filter(_.id === id)
    Await.result(db.run(query.result), Duration.Inf).head
  }

  def insert(vm: VM): Unit = {
    val insertQuery = DBIO.seq(tableQuery += vm)
    Await.result(db.run(insertQuery), Duration.Inf)
  }

  def update(vm: VM): Unit = {
    val updateQuery = tableQuery.filter(_.id === vm.id).update(vm)
    Await.result(db.run(updateQuery), Duration.Inf)
  }
}
