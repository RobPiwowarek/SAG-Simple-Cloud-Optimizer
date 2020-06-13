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

  def findAll(): Seq[TaskSpecification] = {
    val result = Await.result(db.run(tableQuery.result), Duration.Inf)
    result.sortWith((x, y) => x.order < y.order).map(_.toSpec)
  }

  def insert(taskSpecification: TaskSpecification): Unit = {
    val insertQuery = DBIO.seq(tableQuery += taskSpecification.toEntity)
    Await.result(db.run(insertQuery), Duration.Inf)
  }

  def remove(taskId: String): Unit = {
    val deleteQuery = DBIO.seq(tableQuery.filter(_.id === taskId).delete)
    Await.result(db.run(deleteQuery), Duration.Inf)
  }
}
