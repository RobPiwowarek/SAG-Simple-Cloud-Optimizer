package pl.rpw.core.vm.message

class TaskSpecification(val taskId: String,
                        val userId: String,
                        val time: Int,
                        val cpu: Int,
                        val ram: Int,
                        val disk: Int) {

}
