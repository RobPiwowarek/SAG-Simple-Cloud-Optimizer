package pl.rpw.core.local.message

case class VmIsDeadMessage(val vm: String, val tasks: Seq[String])
