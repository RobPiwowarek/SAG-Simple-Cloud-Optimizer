package pl.rpw.core.vm.message

import pl.rpw.core.ResourceType

class TaskSpecification(val taskId: String,
                        val time: Int,
                        val resources: Map[ResourceType.Value, Int]) {

}
