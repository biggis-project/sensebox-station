name := "FlinkDbSink"

version := "3.0"

scalaVersion := "2.11.0"

libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-clients_2.11" % "1.2.0",
  "org.apache.flink" % "flink-streaming-scala_2.11" % "1.2.0",
  "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.2.0",
  "com.typesafe.play" %% "anorm" % "2.5.0",
  "com.typesafe.play" % "play-json_2.11" % "2.5.12",
  "org.scalikejdbc" % "scalikejdbc_2.11" % "2.5.0",
  "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
  //"mysql" % "mysql-connector-java" % "6.0.5", //version throws "java.sql.SQLException: Table name pattern can not be NULL or empty." bei MTable.getTables
  "mysql" % "mysql-connector-java" % "5.1.40"
)

assemblyMergeStrategy in assembly := {
  case PathList("org", "slf4j", "impl", "StaticLoggerBinder.class") => MergeStrategy.first
  case PathList("org", "slf4j", "impl", "StaticMDCBinder.class") => MergeStrategy.first
  case PathList("org", "slf4j", "impl", "StaticMarkerBinder.class") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}