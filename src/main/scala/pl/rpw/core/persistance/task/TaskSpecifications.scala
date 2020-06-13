package pl.rpw.core.persistance.task

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag

class TaskSpecifications(tag: Tag) extends Table[TaskSpecification](tag, "TASKSPECIFCATIONS") {
  def id = column[String]("ID", O.PrimaryKey) // This is the primary key column
  def userId = column[String]("USER_ID")

  def order = column[Int]("ORDER", O.AutoInc)
  def cpu = column[Int]("CPU")
  def ram = column[Int]("RAM")
  def disk = column[Int]("DISK")
  def time = column[Int]("TIME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * =
    (id, userId, time, cpu, ram, disk, order) <> (TaskSpecification.tupled, TaskSpecification.unapply)
}
