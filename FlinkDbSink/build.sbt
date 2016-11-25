name := "FlinkDbSink"

version := "1.0"

scalaVersion := "2.11.0"

libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-clients_2.11" % "1.1.3",
  "org.apache.flink" % "flink-streaming-scala_2.11" % "1.1.3",
  "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.1.3",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
  "org.slf4j" % "slf4j-nop" % "1.6.4"
)
