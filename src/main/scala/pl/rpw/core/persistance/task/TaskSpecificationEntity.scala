package pl.rpw.core.persistance.task

final case class TaskSpecificationEntity(taskId: String,
                                         userId: String,
                                         time: Int,
                                         cpu: Int,
                                         ram: Int,
                                         disk: Int,
                                         order: Int,
                                         var vm: Option[String]) {
  def toSpec: TaskSpecification = {
    TaskSpecification(taskId, userId, time, cpu, ram, disk)
  }

  override def toString: String = {
    s"""TaskSpecificationEntity taskId = $taskId userId = $userId time = $time cpu = $cpu ram = $ram disk = $disk order = $order vm = $vm"""
      .stripMargin
  }
}
