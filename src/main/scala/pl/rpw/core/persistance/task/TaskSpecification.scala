package pl.rpw.core.persistance.task

final case class TaskSpecification(taskId: String,
                                   userId: String,
                                   time: Int,
                                   cpu: Int,
                                   ram: Int,
                                   disk: Int) {
  def toEntity: TaskSpecificationEntity = {
    // fixme: last parameter should be replaced in base by autoincrement I believe.
    TaskSpecificationEntity(taskId, userId, time, cpu, ram, disk, 0, None)
  }

  override def toString: String = {
    s"""TaskSpecification taskId = $taskId userId = $userId time = $time cpu = $cpu ram = $ram disk = $disk """
      .stripMargin
  }
}
