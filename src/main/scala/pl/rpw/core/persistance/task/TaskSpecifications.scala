package pl.rpw.core.persistance.task

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag

class TaskSpecifications(tag: Tag) extends Table[TaskSpecificationEntity](tag, "TASKSPECIFCATIONS") {
  def id = column[String]("ID", O.PrimaryKey) // This is the primary key column
  def userId = column[String]("USER_ID")

  def cpu = column[Int]("CPU")
  def ram = column[Int]("RAM")
  def disk = column[Int]("DISK")
  def time = column[Int]("TIME")
  def order = column[Int]("ORDER", O.AutoInc)
  def vm = column[Option[String]]("VM")

  // Every table needs a * projection with the same type as the table's type parameter
  def * =
    (id, userId, time, cpu, ram, disk, order, vm) <> (TaskSpecificationEntity.tupled, TaskSpecificationEntity.unapply)
}
