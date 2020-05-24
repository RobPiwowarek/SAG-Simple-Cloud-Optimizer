name := "simple-cloud-optimizer"

lazy val commonSettings = Seq(
  organization := s"pl.rpw",
  scalaVersion := "2.11.8",
  scalacOptions := Seq(
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8",
    "-Xcheckinit",
    "-Ywarn-dead-code",
    "-Xfuture",
    "-Ywarn-unused",
    "-feature",
    "-Xfatal-warnings",
    "-language:postfixOps",
    "-language:existentials",
    "-deprecation:false"
  ),
  sources in(Compile, doc) := Seq.empty,
  resolvers ++= Seq(
    Resolver.jcenterRepo,
  )
)

lazy val dependencies = Seq(
  libraryDependencies ++= {
    val akkaV                   = "2.5.31"
    val scalaTestV              = "3.1.1"
    val sparkV                  = "2.2.0"

    Seq(
      "com.typesafe.akka"              %% "akka-actor"                                 % akkaV,
      "com.typesafe.akka"              %% "akka-testkit"                               % akkaV                     % Test,
      "org.scalatest"                  %% "scalatest"                                  % scalaTestV                % Test,
      "org.apache.spark"                % "spark-core_2.11"                            % sparkV,
      "org.apache.spark"                % "spark-sql_2.11"                             % sparkV,
      "org.apache.spark"                % "spark-mllib_2.11"                           % sparkV
)
  }
)

lazy val testSettings = Seq(
  parallelExecution in Test := false,
  testOptions in Test ++= Seq(
    Tests.Argument(TestFrameworks.ScalaTest, "-o"),
    Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports")
  )
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(testSettings)
  .settings(dependencies)
