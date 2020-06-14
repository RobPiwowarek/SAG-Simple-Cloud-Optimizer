package pl.rpw.core.persistance.task

import pl.rpw.core.DBInitializer
import slick.jdbc.H2Profile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object TaskSpecificationsRepository {
  val tableQuery = TableQuery[TaskSpecifications]
  implicit val ec = scala.concurrent.ExecutionContext.global
  val db = DBInitializer.db

  def setup(): Unit = {
    Await.result(db.run(tableQuery.schema.createIfNotExists), Duration.Inf)
  }

  def findAll(): Seq[TaskSpecificationEntity] = {
    val result = Await.result(db.run(tableQuery.result), Duration.Inf)
    result.sortWith((x, y) => x.order < y.order)
  }

  def findByVmIsNull(): Seq[TaskSpecificationEntity] = {
    val searchQuery = tableQuery.filter(_.vm.isEmpty === true).result
    val result = Await.result(db.run(searchQuery), Duration.Inf)
    result.sortWith((x, y) => x.order < y.order)
  }

  def findByVm(vm: String): Seq[TaskSpecificationEntity] = {
    val searchQuery = tableQuery.filter(_.vm === vm).result
    Await.result(db.run(searchQuery), Duration.Inf)
  }

  def findByUserAndVmIsNull(user: String): Seq[TaskSpecificationEntity] = {
    val searchQuery = tableQuery
      .filter(_.userId === user)
      .filter(_.vm.isEmpty === true)
      .result
    val result = Await.result(db.run(searchQuery), Duration.Inf)
    result.sortWith((x, y) => x.order < y.order)
  }

  def insert(taskSpecification: TaskSpecificationEntity): Unit = {
    val insertQuery = DBIO.seq(tableQuery += taskSpecification)
    Await.result(db.run(insertQuery), Duration.Inf)
  }

  def update(taskSpecificationEntity: TaskSpecificationEntity): Unit = {
    val updateQuery = DBIO.seq(tableQuery.filter(_.id === taskSpecificationEntity.taskId).update(taskSpecificationEntity))
    Await.result(db.run(updateQuery), Duration.Inf)
  }

  def remove(taskId: String): Unit = {
    val deleteQuery = DBIO.seq(tableQuery.filter(_.id === taskId).delete)
    Await.result(db.run(deleteQuery), Duration.Inf)
  }
}
