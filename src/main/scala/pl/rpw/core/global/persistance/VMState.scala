package pl.rpw.core.global.persistance

object VMState extends Enumeration {
  val CREATED, IDLE, ACTIVE, DEAD, MIGRATING = Value
}
