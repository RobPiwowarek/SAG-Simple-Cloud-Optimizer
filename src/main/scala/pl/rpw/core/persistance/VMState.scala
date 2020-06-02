package pl.rpw.core.persistance

object VMState extends Enumeration {
  val CREATED, IDLE, ACTIVE, DEAD, MIGRATING = Value
}
