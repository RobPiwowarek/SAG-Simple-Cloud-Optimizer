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
    val slickV                  = "3.3.2"
    val slf4jV                  = "1.6.4"
    val h2V                     = "1.4.200"
    val VmbV                    = "1.1.0"
    val playLiquibaseV          = "2.2"

    Seq(
      "com.typesafe.akka"              %% "akka-actor"                                 % akkaV,
      "com.typesafe.slick"             %% "slick"                                      % slickV,
      // slick wants these 2
      "org.slf4j"                       % "slf4j-nop"                                  % slf4jV,
      "com.typesafe.slick"             %% "slick-hikaricp"                             % slickV,
      "com.h2database"                  % "h2"                                         % h2V,

      "de.aktey.akka.visualmailbox"    %% "collector"                                  % VmbV,
      "de.aktey.akka.visualmailbox"    %% "common"                                     % VmbV,

      "com.typesafe.akka"              %% "akka-testkit"                               % akkaV                     % Test,
      "org.scalatest"                  %% "scalatest"                                  % scalaTestV                % Test,
      "com.ticketfly"                  %% "play-liquibase"                             % playLiquibaseV
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