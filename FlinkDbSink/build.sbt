name := "FlinkDbSink"

version := "1.0"

scalaVersion := "2.11.0"

libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-clients_2.11" % "1.1.3",
  "org.apache.flink" % "flink-streaming-scala_2.11" % "1.1.3",
  "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.1.3",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
  //"mysql" % "mysql-connector-java" % "6.0.5", //version throws "java.sql.SQLException: Table name pattern can not be NULL or empty." bei MTable.getTables
  "mysql" % "mysql-connector-java" % "5.1.40",
  "org.slf4j" % "slf4j-nop" % "1.6.4"
)

assemblyMergeStrategy in assembly := {
  case PathList("org", "slf4j", "impl", "StaticLoggerBinder.class") => MergeStrategy.first
  case PathList("org", "slf4j", "impl", "StaticMDCBinder.class") => MergeStrategy.first
  case PathList("org", "slf4j", "impl", "StaticMarkerBinder.class") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}