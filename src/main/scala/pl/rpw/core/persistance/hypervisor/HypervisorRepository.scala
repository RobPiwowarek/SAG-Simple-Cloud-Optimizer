package pl.rpw.core.persistance.hypervisor

import pl.rpw.core.DBInitializer
import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object HypervisorRepository {
  val tableQuery = TableQuery[Hypervisors]
  implicit val ec = scala.concurrent.ExecutionContext.global
  val db = DBInitializer.db

  def setup(): Unit = {
    Await.result(db.run(tableQuery.schema.createIfNotExists), Duration.Inf)
  }

  def findAll(): Seq[Hypervisor] = {
    Await.result(db.run(tableQuery.result), Duration.Inf)
  }

  def findActiveWithEnoughFreeResources(requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.ACTIVE, requiredCpu, requiredRam, requiredDisk)
  }

  def findIdleWithEnoughFreeResources(requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.IDLE, requiredCpu, requiredRam, requiredDisk)
  }

  def findByStateAndEnoughResources(state: HypervisorState.Value, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    val query = tableQuery
      .filter(_.freeCpu >= requiredCpu)
      .filter(_.freeRam >= requiredRam)
      .filter(_.freeDisk >= requiredDisk)
      .filter(_.state === state.toString())
    Await.result(db.run(query.result), Duration.Inf)
  }

  def findById(id: String): Hypervisor = {
    val query = tableQuery
      .filter(_.id === id)
    Await.result(db.run(query.result), Duration.Inf).head
  }

  def save(hypervisor: Hypervisor): Unit = {
    val insertQuery = DBIO.seq(tableQuery += hypervisor)
    Await.result(db.run(insertQuery), Duration.Inf)
  }
}
