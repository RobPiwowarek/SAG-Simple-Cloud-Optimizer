package pl.rpw.core.persistance

import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HypervisorRepository {
  val tableQuery = TableQuery[Hypervisors]
  implicit val ec = scala.concurrent.ExecutionContext.global

  def setup(): Unit = {
    val db = Database.forConfig("h2mem1")
    try {
      Await.result(db.run(tableQuery.schema.createIfNotExists), Duration.Inf)
    } finally db.close
  }

  def findAll(): Seq[Hypervisor] = {
    val db = Database.forConfig("h2mem1")
    try {
      Await.result(db.run(tableQuery.result), Duration.Inf)
        .map(_ => asInstanceOf[Hypervisor])
    } finally db.close
  }

  def findActiveWithEnoughFreeResources(requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.ACTIVE, requiredCpu, requiredRam, requiredDisk)
  }

  def findIdleWithEnoughFreeResources(requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    findByStateAndEnoughResources(HypervisorState.IDLE, requiredCpu, requiredRam, requiredDisk)
  }

  def findByStateAndEnoughResources(state: HypervisorState.Value, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[Hypervisor] = {
    val db = Database.forConfig("h2mem1")
    try {
      val query = tableQuery
        .filter(_.freeCpu >= requiredCpu)
        .filter(_.freeRam >= requiredRam)
        .filter(_.freeDisk >= requiredDisk)
        .filter(_.state === state.toString())
      Await.result(db.run(query.result), Duration.Inf)
        .map(_ => asInstanceOf[Hypervisor])
    } finally db.close
  }

  def findById(id: String): Hypervisor = {
    val db = Database.forConfig("h2mem1")
    try {
      val query = tableQuery
        .filter(_.id === id)
      Await.result(db.run(query.result), Duration.Inf)
        .map(_ => asInstanceOf[Hypervisor]).head
    } finally db.close
  }

  def save(hypervisor: Hypervisor): Unit = {
    val db = Database.forConfig("h2mem1")
    try {
      val insertQuery = DBIO.seq(tableQuery += hypervisor)
      Await.result(db.run(insertQuery), Duration.Inf)
    } finally db.close
  }
}
