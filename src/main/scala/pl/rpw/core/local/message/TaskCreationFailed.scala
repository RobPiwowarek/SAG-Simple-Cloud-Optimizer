package pl.rpw.core.local.message

import pl.rpw.core.persistance.task.TaskSpecification

final case class TaskCreationFailed(taskSpecification: TaskSpecification)
