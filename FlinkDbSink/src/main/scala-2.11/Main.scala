import java.sql.Connection
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema

import scalikejdbc.ConnectionPool
import anorm._

object Main {
  var inputKafkaTopic: String = "sensebox-measurements"
  var bootstrapServers: String = "localhost:9092"

  var dbConnectionString: String = "jdbc:postgresql:sbm"
  var dbUser: String = null
  var dbPass: String = null

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  //taken from https://github.com/TimothyKlim/anorm-without-play/blob/master/src/main/scala/Main.scala
  //via http://stackoverflow.com/a/25584627
  object DB {
    def withConnection[A](block: Connection => A): A = {
      val connection: Connection = ConnectionPool.borrow()

      try {
        block(connection)
      } finally {
        connection.close()
      }
    }
  }

  def main(args: Array[String]) {
    parseOpts(args)

    println(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    ConnectionPool.singleton(dbConnectionString, dbUser, dbPass)
    //TODO: setupDb() bzw. Tabellenexistenz prüfen (oder zumindest erkennen, wenn die Tabelle nicht existiert und mit sinnvollem Fehler sterben)

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

  def saveMeasurement(ev: ObjectNode): Unit = {
    val boxId = ev.get("boxId").asText
    val sensorId = ev.get("sensor").asText
    val value = ev.get("value").asDouble
    val timestamp = ev.get("createdAt").asText
    val ts = dateFormat.parse(timestamp)
    val sqlTs = new Timestamp(ts.getTime)

    //TODO: Werte prüfen, fehlerhafte loggen (oder in Kafka-Stream?)

    DB.withConnection { implicit connection: Connection =>
      SQL"INSERT INTO measurements(boxid, sensorid, ts, value) VALUES($boxId, $sensorId, $sqlTs, $value)".execute(); //TODO: Fehlerbehandlung
      //TODO: UPSERT/klarkommen, wenn Measurement schon drin weil doppelt ausgeliefert
    }
  }

  def parseOpts(args: Array[String]) {
    //parse environment first, so that values can be overridden by command line args
    if (sys.env.contains("DBSINK_BOOTSTRAP_SERVERS"))    bootstrapServers   = sys.env("DBSINK_BOOTSTRAP_SERVERS")
    if (sys.env.contains("DBSINK_INPUT_KAFKA_TOPIC"))    inputKafkaTopic    = sys.env("DBSINK_INPUT_KAFKA_TOPIC")
    if (sys.env.contains("DBSINK_DB_CONNECTION_STRING")) dbConnectionString = sys.env("DBSINK_DB_CONNECTION_STRING")
    if (sys.env.contains("DBSINK_DB_USER"))              dbUser             = sys.env("DBSINK_DB_USER")
    if (sys.env.contains("DBSINK_DB_PASS"))              dbPass             = sys.env("DBSINK_DB_PASS")

    //TODO: "--db-user=sbm" verarbeitet das natürlich nicht
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