package pl.rpw.core

import com.typesafe.scalalogging.LazyLogging

object HelloLog extends LazyLogging{
  def main(args: Array[String]): Unit = {
    logger.debug("ligma ballz")
  }
}
