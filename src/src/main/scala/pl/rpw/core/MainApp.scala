package pl.rpw.core

import akka.actor.{ActorSystem, Props}
import org.apache.spark.ml.regression.LinearRegression


object MainApp extends App {
  val system = ActorSystem("HelloSystem")
  // default Actor constructor
  val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")
  helloActor ! "hello"
  helloActor ! "buenos dias"
  helloActor ! "I never loved you"
  helloActor ! "me liek cookiez"

  // Load training data
  val training = org.apache.spark.ml.regression.LinearRegression.read.format("libsvm")
    .load("data/mllib/sample_linear_regression_data.txt")

  val lr = new LinearRegression()
    .setMaxIter(10)
    .setRegParam(0.3)
    .setElasticNetParam(0.8)

  // Fit the model
  val lrModel = lr.fit(training)

  // Print the coefficients and intercept for linear regression
  println(s"Coefficients: ${lrModel.coefficients} Intercept: ${lrModel.intercept}")

  // Summarize the model over the training set and print out some metrics
  val trainingSummary = lrModel.summary
  println(s"numIterations: ${trainingSummary.totalIterations}")
  println(s"objectiveHistory: [${trainingSummary.objectiveHistory.mkString(",")}]")
  trainingSummary.residuals.show()
  println(s"RMSE: ${trainingSummary.rootMeanSquaredError}")
  println(s"r2: ${trainingSummary.r2}")

}
