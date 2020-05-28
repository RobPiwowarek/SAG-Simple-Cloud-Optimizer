package pl.rpw.core.global.message

import pl.rpw.core.vm.message.TaskSpecification

case class TaskRequestMessage(val userId: String, val specification: TaskSpecification)
