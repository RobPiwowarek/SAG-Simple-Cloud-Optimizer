package pl.rpw.core

import slick.lifted.Tag
import slick.jdbc.H2Profile.api._

// Definition of the HelloTable table
class HelloTable(tag: Tag) extends Table[(Int, String)](tag, "HELLO") {
  def id = column[Int]("ID", O.PrimaryKey) // This is the primary key column
  def name = column[String]("NAME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id, name)
}