import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema

//import slick.jdbc.JdbcBackend._
//import slick.driver.PostgresDriver.api._
import slick.driver.H2Driver.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  var inputKafkaTopic: String = "sensebox-measurements"
  var bootstrapServers: String = "localhost:9092"

  val db = Database.forURL("jdbc:postgresql:sbm", "sbm", "TODO: aus Cmdline") //TODO: prüfen!
  val measurements = TableQuery[Measurements]

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  def main(args: Array[String]) {
    parseOpts(args)

    println(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    //setupDb

    //TODO: DB-Schema verifizieren? Ggfs. erstellen? Oder nur Fehler werfen?

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

  def setupDb = {
    try {
      //TODO: nur wenn noch nicht existiert
      Await.result(db.run(DBIO.seq(
        // create the schema
        measurements.schema.create
      )), Duration.Inf)
    } //TODO: Fehler melden/verarbeiten
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
      measurements += (boxId, sensorId, sqlTs, value, "")
    )), Duration.Inf) //TODO: sinnvoller
  }

  def parseOpts(args: Array[String]) {
    args.sliding(2, 1).toList.collect {
      case Array("--bootstrap-servers", arg: String) => bootstrapServers = arg
      case Array("--input-kafka-topic", arg: String) => inputKafkaTopic = arg
    }
    //TODO: %ENV auswerten

    // hier prüfen ob nötige Parameter nicht angegeben wurden und ggfs. rumzicken
  }
}