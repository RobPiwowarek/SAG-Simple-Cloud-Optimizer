package pl.rpw.core.persistance.hypervisor

import pl.rpw.core.DBInitializer
import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object HypervisorRepository {
  def findByState(state: HypervisorState.Value): Seq[Hypervisor] = {
    val query = tableQuery
      .filter(_.state === state.toString())
    Await.result(db.run(query.result), Duration.Inf)
  }

  val tableQuery = TableQuery[Hypervisors]
  implicit val ec = scala.concurrent.ExecutionContext.global
  val db = DBInitializer.db

  def setup(): Unit = {
    Await.result(db.run(tableQuery.schema.createIfNotExists), Duration.Inf)
  }

  def findAll(): Seq[Hypervisor] = {
    Await.result(db.run(tableQuery.result), Duration.Inf)
  }

  def findActiveWithEnoughFreeResourcesAndIdNot(requiredCpu: Int,
                                                requiredRam: Int,
                                                requiredDisk: Int,
                                                excludedId: String): Seq[Hypervisor] = {
    findByStateAndEnoughResourcesAndIdNot(HypervisorState.ACTIVE, requiredCpu, requiredRam, requiredDisk, excludedId)
  }

  def findIdleWithEnoughFreeResourcesAndIdNot(requiredCpu: Int,
                                              requiredRam: Int,
                                              requiredDisk: Int,
                                              excludedId: String): Seq[Hypervisor] = {
    findByStateAndEnoughResourcesAndIdNot(HypervisorState.IDLE, requiredCpu, requiredRam, requiredDisk, excludedId)
  }

  def findByStateAndEnoughResourcesAndIdNot(state: HypervisorState.Value,
                                            requiredCpu: Int,
                                            requiredRam: Int,
                                            requiredDisk: Int,
                                            excludedId: String): Seq[Hypervisor] = {
    val query = tableQuery
      .filter(_.freeCpu >= requiredCpu)
      .filter(_.freeRam >= requiredRam)
      .filter(_.freeDisk >= requiredDisk)
      .filter(_.state === state.toString())
      .filter(_.id =!= excludedId)
    Await.result(db.run(query.result), Duration.Inf)
  }

  def findActiveWithEnoughFreeResources(requiredCpu: Int,
                                        requiredRam: Int,
                                        requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.ACTIVE, requiredCpu, requiredRam, requiredDisk)
  }

  def findIdleWithEnoughFreeResources(requiredCpu: Int,
                                      requiredRam: Int,
                                      requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.IDLE, requiredCpu, requiredRam, requiredDisk)
  }

  def findByStateAndEnoughResources(state: HypervisorState.Value,
                                    requiredCpu: Int,
                                    requiredRam: Int,
                                    requiredDisk: Int): Seq[Hypervisor] = {
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
    Await.result(db.run(query.result), Duration.Inf).headOption.orNull
  }

  def insert(hypervisor: Hypervisor): Unit = {
    val insertQuery = DBIO.seq(tableQuery += hypervisor)
    Await.result(db.run(insertQuery), Duration.Inf)
  }

  def update(hypervisor: Hypervisor): Unit = {
    val updateQuery = tableQuery.filter(_.id === hypervisor.id).update(hypervisor)
    Await.result(db.run(updateQuery), Duration.Inf)
  }
}
