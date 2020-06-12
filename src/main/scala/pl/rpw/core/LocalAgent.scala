package pl.rpw.core

import java.io.{File, FileWriter}
import java.time.LocalDateTime

import akka.actor.Actor
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.{GBTRegressionModel, GBTRegressor}
import org.apache.spark.sql.{SQLContext, SparkSession}
import org.apache.spark.{SparkContext, sql}
import pl.rpw.core.LocalAgent.{GetNewVM, StartAgent}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class LocalAgent extends Actor{

  //creating Spark object
  private[this] val spark = SparkSession.builder().master("local[*]").getOrCreate()
  spark.sparkContext.setLogLevel("ERROR")
  val sparkContext: SparkContext = spark.sparkContext
  val sqlContext: SQLContext = spark.sqlContext

  val n_iterations = 5000 //number of iterations in which the prediction is made
  val n_iterations_retrain = 1000 //number of iterations after which the model is refreshed
  val rootFilePath = "/home/andy/Desktop/sag_projekt/src/data/"
  //  val rootFilePath = "./../../../../../data"
  val historyFilePath = rootFilePath+"history.txt" //filepath for time series of usage history

  var n_predictions: Int = 0
  var cpu:Int = 0
  var gpu:Int = 0
  var disk:Int = 0

  var historyCPU_fp:String = ""
  var historyGPU_fp:String = ""
  var historyDisk_fp:String = ""

  var usageHistoryCPU = mutable.Stack(0.0d)
  var usageHistoryGPU = mutable.Stack(0.0d)
  var usageHistoryDisk = mutable.Stack(0.0d)

  var modelCPU: GBTRegressionModel = null
  var modelGPU: GBTRegressionModel = null
  var modelDisk: GBTRegressionModel = null

  def receive = {
    /**
    create model basing on history loaded from file
    generate values for sin(t) and predict values for t+1
    at the end save new history usage to the file
     */

    case StartAgent(cpu, gpu, disk) =>
      this.cpu = cpu
      this.gpu = gpu
      this.disk = disk

      println(s"Creating actor with parameters: $cpu, $gpu, $disk ")
      this.historyCPU_fp = rootFilePath + s"cpu_A=$cpu.txt"
      this.historyGPU_fp = rootFilePath + s"gpu_A=$gpu.txt"
      this.historyDisk_fp = rootFilePath + s"disk_A=$disk.txt"

      //generating history for the model
      this.createUsageHistory("cpu", historyCPU_fp, cpu, 1000)
      this.createUsageHistory("gpu", historyGPU_fp, gpu, 1000)
      this.createUsageHistory("disk", historyDisk_fp, disk, 1000)
      println("Default history created, starting preparing the models.")

      //creating models
      modelCPU = this.prepareNewModel(historyCPU_fp)
      println("CPU model created successfully.")

      modelGPU = this.prepareNewModel(historyGPU_fp)
      println("GPU model created successfully")

      modelDisk = this.prepareNewModel(historyDisk_fp)
      println("Disk model created successfully")

      sender ! "Agent created"

    case GetNewVM(conservativenessRate) =>
      //predicting resources usage
      val cpuPrediction = this.predict(this.modelCPU, this.usageHistoryCPU ) * conservativenessRate
      val gpuPrediction = this.predict(this.modelGPU, this.usageHistoryGPU ) * conservativenessRate
      val diskPrediction = this.predict(this.modelDisk, this.usageHistoryDisk ) * conservativenessRate

      //appending real usage to the history of usages
      this.usageHistoryCPU.push(this.cpu*math.sin(math.toRadians(this.n_predictions)))
      this.usageHistoryGPU.push(this.gpu*math.sin(math.toRadians(this.n_predictions)))
      this.usageHistoryDisk.push(this.disk*math.sin(math.toRadians(this.n_predictions)))

      this.n_predictions += 1

      val result = Map(cpu -> cpuPrediction, gpu -> gpuPrediction, disk -> diskPrediction)

      println("Adding new VM.")
      sender ! result

    case "ShutDown" =>
      context.system.terminate()

    case "test" =>
      println("Starting new agent")
      //loading history of data usage and training model
      var model = this.prepareNewModel(historyFilePath)
      var x : Int = 1
      var value : Double= 0.0d
      var predictedVal: Double = 0.0d
      val usageHistory = mutable.Stack(0.0d)
      val predictedHistory = mutable.Stack(0.0d)

      //working on a fake task which is calculating sin function
      while(x<n_iterations){
        //retrain the model on new data after n_iterations_retrain iterations
        if (x%n_iterations_retrain == 0){
          this.writeHistoryToFile(historyFilePath, usageHistory.reverse,true)
          model = this.prepareNewModel(historyFilePath)
        }
        // generate value
        value = math.sin(math.toRadians(x))

        //predict value
        predictedVal = this.predict(model, usageHistory)

        predictedHistory.push(predictedVal)
        usageHistory.push(value)

        sender() ! predictedVal.toString
        x=x+1
      }
      //saving real data usage and predictions to make visualizations of the process
      this.writeHistoryToFile("/home/andy/Desktop/data/usage.txt", usageHistory, false)
      this.writeHistoryToFile("/home/andy/Desktop/data/predicted.txt", predictedHistory, false)

      println("done!")
      sender() ! "The task is done!"

  }
  /**
  pipeline for creating new GBT model
  the model is trained on history saved in the file from filePath
   */
  private[this] def prepareNewModel(filePath: String): GBTRegressionModel ={
    val history = this.loadTimeseriesFromTxt(filePath)
    val training = this.prepareTrainingDataset(history)
    this.createGradientBoostedTreeRegressionModel(training)
  }
  /**
  writing stack to file
  if append is set to true, the values are appended
  if it is set to false the values are overeaten
  */
  private[this] def writeHistoryToFile(filePath: String,
                                       history: mutable.Stack[Double], append: Boolean){
    val writer = new FileWriter(new File(filePath), append)
    for(item <- history ){
      writer.write(LocalDateTime.now().toString()+";"+item.toString()+'\n')
    }
    writer.close()
  }
  /**
  Creating usage history and writing it to the file.
  The sin shape is based of the parameter
  */
  private[this] def createUsageHistory(resourceType: String,
                                       filePath: String,
                                       amplitude:Double,
                                       fileLength: Int): Unit ={
    var value: Double = 0.0d
    val usageHistory = mutable.Stack(0.0d)

    for (x <- 0 to fileLength){
      value = amplitude*math.sin(math.toRadians(x))
      usageHistory.push(value)
      this.n_predictions += 1
    }
    if (resourceType == "cpu"){
      this.usageHistoryCPU = usageHistory
    } else if (resourceType == "gpu"){
      this.usageHistoryGPU = usageHistory
    } else if (resourceType == "disk"){
      this.usageHistoryDisk = usageHistory
    }
    writeHistoryToFile(filePath, usageHistory, false)

  }

  /**
  use the model to predict value in t+1, basing on values from range t-n+1 : t
   */
  private[this] def predict(model: GBTRegressionModel,
                            historyList: mutable.Stack[Double]):Double = {
    //the model can be used only if the history is longer than the look back of the model
    //if it is shorter we return the previous value
    if(historyList.size < 12){
        return historyList(0)
      }

    val values = List.concat(ListBuffer(0.0d), historyList.take(10))
    import spark.implicits._
    val values_df = List(values).map(x => (x(0), x(1), x(2), x(3), x(4), x(5), x(6), x(7), x(8), x(9), x(10))).toDF()

    //creating features vector
    val assembler = new VectorAssembler().
      setInputCols(Array("_2", "_3", "_4", "_5", "_6", "_7", "_8", "_9", "_10", "_11")).
      setOutputCol("features")

    val row = assembler.transform(values_df)
    model.transform(row).select("prediction").collectAsList().get(0).getDouble(0)
  }


  /**
  reshaping timeseries of shape 1 x n to m x n,
  where m is the size of previous values which
  are used as the attributes for the model
   */
  private[this] def temporize(valuesList:List[String]): ListBuffer[ListBuffer[Float]] = {
    val output_X = new ListBuffer[ListBuffer[Float]]()
    val step = 11
    for( i <- 0 to valuesList.size - step - 1){
      val t = new ListBuffer[Float]()
      for (j <- 1 to step){
        //Gather past records up to the step generationPeriod
        t.append(valuesList((i+j-1).toInt).toFloat)
      }
      output_X.append(t)
    }
    output_X
  }

  /**
   loads timeseries data from txt and converts it to dataframe
   */
  private[this] def loadTimeseriesFromTxt(filePath: String): sql.DataFrame = {
    var df = spark.read
      .option("header", "false")
      .option("sep", ";")
      .csv(filePath)
    val newNames = Seq("timestamp", "value")
    df = df.toDF(newNames: _*)
    df
  }

  /**
   reshapes appends n past values to each record in dataframe
   and transforms it to the form in which it can be used by the model
   */
  private[this] def prepareTrainingDataset(df: sql.DataFrame): sql.DataFrame = {
    import spark.implicits._
    var valueList = df.select("value").map(r => r.getString(0)).collect.toList

    //reversing list to get the newest values first and limiting its length to 1000 elements
    valueList = valueList.reverse.take(n_iterations_retrain)
    //reshaping data
    val temporized_list = this.temporize(valueList)

    //converting list to data frame
    val df_temporized = temporized_list.map(x => (x(0), x(1), x(2), x(3), x(4), x(5), x(6), x(7), x(8), x(9), x(10))).toDF()

    //creating features vector
    val assembler = new VectorAssembler().
      setInputCols(Array("_2", "_3", "_4", "_5", "_6", "_7", "_8", "_9", "_10", "_11")).
      setOutputCol("features")
    val training = assembler.transform(df_temporized)
    training
  }
  /**
  creates GBT model
   */
  private[this] def createGradientBoostedTreeRegressionModel(training: sql.DataFrame): GBTRegressionModel = {
    val gbt = new GBTRegressor()
    .setLabelCol("_1")
    .setMaxIter(10)

    // Train model. This also runs the indexer.
    val model = gbt.fit(training)

    // Make predictions.
    val predictions = model.transform(training)

    // Select (prediction, true label) and compute test error.
    val evaluator = new RegressionEvaluator()
    .setLabelCol("_1")
    .setPredictionCol("prediction")
    .setMetricName("rmse")

    val rmse = evaluator.evaluate(predictions)
    println(s"Model created. RMSE = $rmse")
    model
  }
}

object LocalAgent {
  case class StartAgent(cpu: Int, gpu: Int, disk: Int)

  // conservativenessRate is a value which says how conservative the prediction should be
  // if it is set to the value greater than zero the result will be greater than the one returned by the model
  // if it is smaller than 1 the result will be smaller than the one returned by the model
  case class GetNewVM(conservativenessRate: Double)

  final case class GenerateTask()
}
