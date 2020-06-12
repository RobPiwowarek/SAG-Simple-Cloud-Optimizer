package pl.rpw.core.vm.message

class TaskSpecification(val taskId: String,
                        val userId: String,
                        val time: Int,
                        val cpu: Int,
                        val ram: Int,
                        val disk: Int) {
  override def toString: String = {
    s"""TaskSpecification taskId = $taskId userId = $userId time = $time cpu = $cpu ram = $ram disk = $disk """
      .stripMargin
  }
}
