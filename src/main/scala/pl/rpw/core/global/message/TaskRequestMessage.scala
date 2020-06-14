package pl.rpw.core.global.message

import pl.rpw.core.persistance.task.TaskSpecification

case class TaskRequestMessage(val specification: TaskSpecification)
