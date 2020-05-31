package pl.rpw.core.global.persistance

import slick.lifted.Tag
import slick.jdbc.H2Profile.api._

class VM (tag: Tag) extends Table[(String, String, Int, Int, Int)](tag, "HIPERVISOR") {
  def id = column[String]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def state = column[String]("STATE")
  def cpu = column[Int]("CPU")
  def ram = column[Int]("RAM")
  def disk = column[Int]("DISK")
  def user = column[String]("USER")
  def hipervisor = column[String]("HIPERVISOR")
  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id, state, cpu, ram, disk, user, hipervisor)
}
