import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema

//import slick.jdbc.JdbcBackend._
//import slick.driver.PostgresDriver.api._
//import slick.driver.H2Driver.api._
import slick.driver.MySQLDriver.api._
import slick.lifted.TableQuery
import slick.jdbc.meta.MTable

import scala.concurrent.Await
import scala.concurrent.duration._

object Main {
  var inputKafkaTopic: String = "sensebox-measurements"
  var bootstrapServers: String = "localhost:9092"

  var dbConnectionString: String = "jdbc:postgresql:sbm"
  var dbUser: String = null
  var dbPass: String = null

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  var db: Database = null
  val measurements = TableQuery[Measurements]

  def main(args: Array[String]) {
    parseOpts(args)

    println(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    db = connectDb

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val properties = new Properties {
      put("bootstrap.servers", bootstrapServers)
    }
    val stream = env
      .addSource(function = new FlinkKafkaConsumer09[ObjectNode](inputKafkaTopic, new JSONDeserializationSchema(), properties))
      .addSink(el => saveMeasurement(el))

    //TODO: kann man die Source/Sink irgendwie sinnvoll benennen, damit das in der Management Console schöner aussieht?
    env.execute("Sensebox Measurements DB Sink")
  }

  def connectDb = {
    println(s"Using database ${dbConnectionString}, User ${dbUser}")
    val db = Database.forURL(dbConnectionString, dbUser, dbPass) //TODO: prüfen!
    setupDb(db)

    db
  }

  def setupDb(db: Database) = {
    val tables = Await.result(db.run(MTable.getTables), 15.seconds)
    val measurementsTables = tables.filter(t => t.name.name == "measurements" && t.name.schema.get == "public")//TODO: das ist Postgres-spezifisch (nur auf TabNamen?)

    if (measurementsTables.size < 1) {
      println("Creating table for SenseBox measurements ...")
      println(measurements.schema.create.statements)
      try {
        Await.result(db.run(DBIO.seq(
          // create the schema
          measurements.schema.create
        )), Duration.Inf)
      } //TODO: Fehler melden/verarbeiten
    }
  }

  def saveMeasurement(ev: ObjectNode): Unit = {
    val boxId = ev.get("boxId").asText
    val sensorId = ev.get("sensor").asText
    val value = ev.get("value").asDouble
    val timestamp = ev.get("createdAt").asText
    val ts = dateFormat.parse(timestamp)
    val sqlTs = new Timestamp(ts.getTime)

    //TODO: Werte prüfen, fehlerhafte loggen (oder in Kafka-Stream?)

    Await.result(db.run(DBIO.seq(
      measurements += (boxId, sensorId, sqlTs, value, null)
    )), Duration.Inf) //TODO: sinnvoller (bspw. Futures sammeln. Kann man da irgendwie die fertigen abfragen ohne wait()?) Dann die fertigen verarbeiten (ggfs. Fehlermeldung), der Großteil dürfte ohne weitere Bearbeitung erledigt sein.
  }

  def parseOpts(args: Array[String]) {
    //parse environment first, so that values can be overridden by command line args
    if (sys.env.contains("DBSINK_BOOTSTRAP_SERVERS"))    bootstrapServers   = sys.env("DBSINK_BOOTSTRAP_SERVERS")
    if (sys.env.contains("DBSINK_INPUT_KAFKA_TOPIC"))    inputKafkaTopic    = sys.env("DBSINK_INPUT_KAFKA_TOPIC")
    if (sys.env.contains("DBSINK_DB_CONNECTION_STRING")) dbConnectionString = sys.env("DBSINK_DB_CONNECTION_STRING")
    if (sys.env.contains("DBSINK_DB_USER"))              dbUser             = sys.env("DBSINK_DB_USER")
    if (sys.env.contains("DBSINK_DB_PASS"))              dbPass             = sys.env("DBSINK_DB_PASS")

    args.sliding(2, 1).toList.collect {
      case Array("--bootstrap-servers", arg: String)    => bootstrapServers   = arg
      case Array("--input-kafka-topic", arg: String)    => inputKafkaTopic    = arg
      case Array("--db-connection-string", arg: String) => dbConnectionString = arg
      case Array("--db-user", arg: String)              => dbUser             = arg
      case Array("--db-pass", arg: String)              => dbPass             = arg
    }

    // hier prüfen ob nötige Parameter nicht angegeben wurden und ggfs. rumzicken
  }
}