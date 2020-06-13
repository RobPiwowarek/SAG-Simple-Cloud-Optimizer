package pl.rpw.core.persistance.task

final case class TaskSpecification(taskId: String,
                                   userId: String,
                                   time: Int,
                                   cpu: Int,
                                   ram: Int,
                                   disk: Int,
                                   order: Int) {
  override def toString: String = {
    s"""TaskSpecification taskId = $taskId userId = $userId time = $time cpu = $cpu ram = $ram disk = $disk """
      .stripMargin
  }
}
