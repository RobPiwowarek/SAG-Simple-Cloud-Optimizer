package pl.rpw.core.persistance.hypervisor

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag

class Hypervisors(tag: Tag) extends Table[Hypervisor](tag, "HYPERVISORS") {
  def id = column[String]("ID", O.PrimaryKey) // This is the primary key column
  def state = column[String]("STATE")

  def cpu = column[Int]("CPU")

  def ram = column[Int]("RAM")

  def disk = column[Int]("DISK")

  def freeCpu = column[Int]("FREE_CPU")

  def freeRam = column[Int]("FREE_RAM")

  def freeDisk = column[Int]("FREE_DISK")

  // Every table needs a * projection with the same type as the table's type parameter
  def * =
    (id, state, cpu, ram, disk, freeCpu, freeRam, freeDisk) <> (Hypervisor.tupled, Hypervisor.unapply)
}
