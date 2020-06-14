package pl.rpw.core.persistance.vm

object VMState extends Enumeration {
  val CREATED, IDLE, ACTIVE, DEAD, MIGRATING = Value
}
