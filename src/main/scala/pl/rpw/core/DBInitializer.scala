package pl.rpw.core

import slick.jdbc.H2Profile.api._

object DBInitializer {
  val db = Database.forConfig("h2mem1")
}
