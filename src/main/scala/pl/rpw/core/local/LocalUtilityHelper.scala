package pl.rpw.core.local

import java.io.{File, FileWriter}
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.{GBTRegressionModel, GBTRegressor}
import org.apache.spark.sql.{SQLContext, SparkSession}
import org.apache.spark.{SparkContext, sql}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object LocalUtilityHelper extends LazyLogging {

  //creating Spark object
  private[this] val spark = SparkSession.builder().master("local[*]").getOrCreate()
  spark.sparkContext.setLogLevel("INFO")
  val sparkContext: SparkContext = spark.sparkContext
  val sqlContext: SQLContext = spark.sqlContext

  val trainingSetSize = 50

  def init = {
    logger.info("Initializing Spark session finished")
  }

  /**
   * pipeline for creating new GBT model
   * the model is trained on history saved in the file from filePath
   */
  def initNewModel(filePath: String): GBTRegressionModel = {
    val history = this.loadTimeseriesFromTxt(filePath)
    val training = this.prepareTrainingDataset(history)
    this.createGradientBoostedTreeRegressionModel(training)
  }

  /**
   * writing stack to file
   * if append is set to true, the values are appended
   * if it is set to false the values are overeaten
   */
  def writeHistoryToFile(filePath: String,
                         history: mutable.Stack[Sample],
                         append: Boolean) {
    val writer = new FileWriter(new File(filePath), append)
    for (item <- history) {
      writer.write(item.timestamp.toString + ";" + item.usage.toString + '\n')
    }
    writer.close()
  }

  /**
   * Creating usage history and writing it to the file.
   * The sin shape is based of the parameter
   */
  def initUsageHistory(filePath: String, fileLength: Int): Unit = {
    val usageHistory = mutable.Stack[Sample]()

    for (x <- 0 to fileLength) {
      val value = 0.0
      usageHistory.push(Sample(value, LocalDateTime.now()))
    }
    writeHistoryToFile(filePath, usageHistory, false)
  }


  /**
   * use the model to predict value in t+1, basing on values from range t-n+1 : t
   */
  def predict(model: GBTRegressionModel,
              history: mutable.Stack[Sample]): Double = {
    //the model can be used only if the history is longer than the look back of the model
    //if it is shorter we return the previous value
    if (history.size < 12) {
      return history(0).usage
    }

    val values = List.concat(ListBuffer(0.0d), history.take(trainingSetSize).map(_.usage))
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
   * reshaping timeseries of shape 1 x n to m x n,
   * where m is the size of previous values which
   * are used as the attributes for the model
   */
  private[this] def temporize(valuesList: List[String]): ListBuffer[ListBuffer[Float]] = {
    val output_X = new ListBuffer[ListBuffer[Float]]()
    val step = 11
    for (i <- 0 to valuesList.size - step - 1) {
      val t = new ListBuffer[Float]()
      for (j <- 1 to step) {
        //Gather past records up to the step generationPeriod
        t.append(valuesList((i + j - 1).toInt).toFloat)
      }
      output_X.append(t)
    }
    output_X
  }

  /**
   * loads timeseries data from txt and converts it to dataframe
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
   * reshapes appends n past values to each record in dataframe
   * and transforms it to the form in which it can be used by the model
   */
  private[this] def prepareTrainingDataset(df: sql.DataFrame): sql.DataFrame = {
    import spark.implicits._
    var valueList = df.select("value").map(r => r.getString(0)).collect.toList

    //reversing list to get the newest values first and limiting its length to 1000 elements
    valueList = valueList.reverse.take(1000)
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
   * creates GBT model
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

