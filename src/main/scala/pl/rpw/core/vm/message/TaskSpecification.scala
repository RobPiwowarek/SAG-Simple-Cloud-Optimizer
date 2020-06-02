package pl.rpw.core.vm.message

import pl.rpw.core.ResourceType

class TaskSpecification(val taskId: String,
                        val time: Int,
                        val cpu: Int,
                        val ram: Int,
                        val disk: Int) {

}
