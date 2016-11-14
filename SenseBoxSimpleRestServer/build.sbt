name := "sensebox-simple-rest-server"

version := "0.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  cache,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test,
  "org.apache.kafka" % "kafka-clients" % "0.9.0.0"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}