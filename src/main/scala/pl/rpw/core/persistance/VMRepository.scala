package pl.rpw.core.persistance

import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class VMRepository {
  val tableQuery = TableQuery[VMs]
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
      Await.result(
        db.run(tableQuery.result),
        Duration.Inf).map(_ => asInstanceOf[Hypervisor])
    } finally db.close
  }

  def findActiveWithEnoughFreeResources(userId: String, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    findByUserAndStateAndEnoughResources(userId, VMState.ACTIVE, requiredCpu, requiredRam, requiredDisk)
  }

  def findIdleWithEnoughFreeResources(userId: String, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    findByUserAndStateAndEnoughResources(userId, VMState.IDLE, requiredCpu, requiredRam, requiredDisk)
  }

  def findByUserAndStateAndEnoughResources(userId: String, state: VMState.Value, requiredCpu: Int, requiredRam: Int, requiredDisk: Int): Seq[VM] = {
    val db = Database.forConfig("h2mem1")
    try {
      val query = tableQuery
        .filter(_.user === userId)
        .filter(_.freeCpu >= requiredCpu)
        .filter(_.freeRam >= requiredRam)
        .filter(_.freeDisk >= requiredDisk)
        .filter(_.state === state.toString)
      Await.result(
        db.run(query.result),
        Duration.Inf).map(_ => asInstanceOf[VM])
    } finally db.close
  }

  def findById(id: String): VM = {
    val db = Database.forConfig("h2mem1")
    try {
      val query = tableQuery
        .filter(_.id === id)
      Await.result(
        db.run(query.result),
        Duration.Inf).map(_ => asInstanceOf[VM]).head
    } finally db.close
  }

  def save(vm: VM): Unit = {
    val db = Database.forConfig("h2mem1")
    try {
      val insertQuery = DBIO.seq(tableQuery += vm)
      Await.result(db.run(insertQuery), Duration.Inf)
    } finally db.close
  }
}
