package pl.rpw.core.local

import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.util.UUID

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.ml.regression.GBTRegressionModel
import pl.rpw.core.Utils
import pl.rpw.core.global.message.{TaskFinishedMessage, TaskRequestMessage, VirtualMachineRequestMassage}
import pl.rpw.core.hipervisor.message.VirtualMachineSpecification
import pl.rpw.core.local.message.{CreateVMMessage, TaskCreationFailed, TaskGenerationRequestMessage, VMCreated, VmIsDeadMessage}
import pl.rpw.core.persistance.task.TaskSpecification

import scala.collection.mutable

class LocalUtilityActor(val id: String,
                        val cpuAmplitude: Double,
                        val ramAmplitude: Double,
                        val diskAmplitude: Double,
                        val timeAmplitude: Double,
                        val taskGenerationPeriod: Int
                       ) extends Actor with LazyLogging {
  val actorSystem = context.system

  var tasks = new mutable.HashMap[String, TaskSpecification]()
  val vms = new mutable.HashSet[String]()

  val rootFilePath = "data"
  val historyFilePath = "_history.txt" //filepath for time series of usage history

  val historyCpuPath: String = s"""$rootFilePath/cpu_$id$historyFilePath"""
  val historyRamPath: String = s"""$rootFilePath/ram_$id$historyFilePath"""
  val historyDiskPath: String = s"""$rootFilePath/disk_$id$historyFilePath"""

  var currentCpuUsage = 0
  var currentRamUsage = 0
  var currentDiskUsage = 0

  var usageHistoryCPU = mutable.Stack[Sample]()
  var usageHistoryRam = mutable.Stack[Sample]()
  var usageHistoryDisk = mutable.Stack[Sample]()

  if (!Files.exists(Paths.get(historyFilePath))) {
    LocalUtilityHelper.initUsageHistory(historyCpuPath, 1000)
    LocalUtilityHelper.initUsageHistory(historyRamPath, 1000)
    LocalUtilityHelper.initUsageHistory(historyDiskPath, 1000)
  }

  var modelCPU: GBTRegressionModel = LocalUtilityHelper.initNewModel(historyCpuPath)
  var modelRam: GBTRegressionModel = LocalUtilityHelper.initNewModel(historyRamPath)
  var modelDisk: GBTRegressionModel = LocalUtilityHelper.initNewModel(historyDiskPath)

  def adjustSpecification(specification: VirtualMachineSpecification): VirtualMachineSpecification = {
    if (vms.isEmpty) {
      specification
    } else {
      val predictedCpu = LocalUtilityHelper.predict(modelCPU, usageHistoryCPU).toInt / vms.size
      val predictedRam = LocalUtilityHelper.predict(modelRam, usageHistoryRam).toInt / vms.size
      val predictedDisk = LocalUtilityHelper.predict(modelDisk, usageHistoryDisk).toInt / vms.size

      val adjusted = new VirtualMachineSpecification(
        if (predictedCpu == 0) specification.cpu else math.abs(0.2 * predictedCpu + 0.8 * specification.cpu).toInt,
        if (predictedRam == 0) specification.ram else math.abs(0.2 * predictedRam + 0.8 * specification.ram).toInt,
        if (predictedDisk == 0) specification.disk else math.abs(0.2 * predictedDisk + 0.8 * specification.disk).toInt
      )
      logger.info(s"Adjusted spec on $id: $adjusted. Requested $specification")
      adjusted
    }
  }

  def updateModels() = {
    modelCPU = LocalUtilityHelper.initNewModel(historyCpuPath)
    modelRam = LocalUtilityHelper.initNewModel(historyRamPath)
    modelDisk = LocalUtilityHelper.initNewModel(historyDiskPath)
  }

  def increaseUsage(specification: TaskSpecification) = {
    currentCpuUsage += specification.cpu
    currentRamUsage += specification.ram
    currentDiskUsage += specification.disk
    updateHistory(currentCpuUsage, currentRamUsage, currentDiskUsage)
  }

  def decreaseUsage(specification: TaskSpecification) = {
    currentCpuUsage -= specification.cpu
    currentRamUsage -= specification.ram
    currentDiskUsage -= specification.disk
    updateHistory(currentCpuUsage, currentRamUsage, currentDiskUsage)
  }

  def updateHistory(cpu: Int, ram: Int, disk: Int): Unit = {
    usageHistoryCPU.push(Sample(cpu, LocalDateTime.now()))
    LocalUtilityHelper.writeHistoryToFile(historyCpuPath, usageHistoryCPU, false)
    usageHistoryRam.push(Sample(ram, LocalDateTime.now()))
    LocalUtilityHelper.writeHistoryToFile(historyRamPath, usageHistoryRam, false)
    usageHistoryDisk.push(Sample(disk, LocalDateTime.now()))
    LocalUtilityHelper.writeHistoryToFile(historyDiskPath, usageHistoryDisk, false)
  }

  override def receive: Receive = {
    case CreateVMMessage(specification) =>
      if (usageHistoryCPU.size >= LocalUtilityHelper.trainingSetSize
        && usageHistoryRam.size >= LocalUtilityHelper.trainingSetSize
        && usageHistoryDisk.size >= LocalUtilityHelper.trainingSetSize) {
        updateModels()
      }

      logger.info(s"""Local agent $id sending $specification to Global Utility Actor""")
      val adjustedSpecification = adjustSpecification(specification)
      val globalRef = Utils.globalUtility(actorSystem)
      globalRef ! VirtualMachineRequestMassage(id, adjustedSpecification)

    case TaskGenerationRequestMessage() =>
      if (vms.nonEmpty) {
        logger.info(s"""Local agent $id is generating task...""")
        val specification = generateTask()
        tasks.put(specification.taskId, specification)
        increaseUsage(specification)
        val globalRef = Utils.globalUtility(actorSystem)
        globalRef ! TaskRequestMessage(specification)
      }

    case TaskFinishedMessage(taskId, userId) =>
      logger.info(s"""Task $taskId was finished""")
      tasks.get(taskId).map(t => {
        decreaseUsage(t)
        tasks.remove(taskId)
      })

    case TaskCreationFailed(taskId) =>
      logger.info(s"""Task $taskId could not be executed or enqueued""")
      tasks.get(taskId).map(t => {
        decreaseUsage(t)
        tasks.remove(taskId)
      })

    case VMCreated(id) =>
      logger.info(s"""VM $id requested by local agent ${this.id} was created""")
      vms.add(id)

    case VmIsDeadMessage(vm, tasks) =>
      logger.info(s"""VM $id was found dead""")
      vms.remove(vm)
      tasks.foreach(task => {
        this.tasks.get(task).map(t => {
          println(s"Removing dead task $task")
          decreaseUsage(t)
          this.tasks.remove(task)
        })
      })

    case any =>
      println(any)
  }

  private def generateTask() = {
    val timestamp = System.currentTimeMillis()
    TaskSpecification(
      UUID.randomUUID().toString,
      id,
      (timeAmplitude * math.abs(math.sin(timestamp))).toInt + 1, // to avoid 0 time
      (cpuAmplitude * math.abs(math.sin(timestamp))).toInt + 1,
      (ramAmplitude * math.abs(math.sin(timestamp))).toInt + 1,
      (diskAmplitude * math.abs(math.sin(timestamp))).toInt + 1
    )
  }

}
