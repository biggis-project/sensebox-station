name := "FlinkDbSink"

version := "1.0"

scalaVersion := "2.11.0"

libraryDependencies += "org.apache.flink" % "flink-clients_2.11" % "1.1.3"
libraryDependencies += "org.apache.flink" % "flink-streaming-scala_2.11" % "1.1.3"
libraryDependencies += "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.1.3"
