package pl.rpw.core

// Use H2Profile to connect to an H2 database
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object HelloSlick {

  def main(args: Array[String]): Unit = {
    val tableQuery = TableQuery[HelloTable]
    implicit val ec = scala.concurrent.ExecutionContext.global

    val db = Database.forConfig("h2mem1")
    try {

      val setup = DBIO.seq(
        tableQuery.schema.createIfNotExists,
        tableQuery += (0, "Paulina"),
        tableQuery += (1, "Robert"),
        tableQuery += (2, "Dawid")
      )

      val setupFuture = db.run(setup)

      val resultFuture = setupFuture.flatMap { _ =>
        println("Names:")
        db.run(tableQuery.result).map(_.foreach {
          case (id, name) =>
            println("  " + name + "\t" + id)
        })
      }

      Await.result(resultFuture, Duration.Inf)
    } finally db.close
  }
}
