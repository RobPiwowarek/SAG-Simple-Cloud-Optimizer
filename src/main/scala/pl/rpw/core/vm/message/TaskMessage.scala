package pl.rpw.core.vm.message

import pl.rpw.core.persistance.task.TaskSpecification

final case class TaskMessage(specification: TaskSpecification)
