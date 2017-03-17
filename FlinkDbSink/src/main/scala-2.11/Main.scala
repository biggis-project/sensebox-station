import java.sql.Connection
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool

import play.api.libs.json._

import scalikejdbc.ConnectionPool
import anorm._

import org.slf4j.LoggerFactory
import org.slf4j.Logger

object Main {
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

  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.main")

    val paramCmdline = ParameterTool.fromArgs(args)
    val paramDefaults = ParameterTool.fromArgs(Array("--bootstrap-servers", "localhost:9092",
                                                     "--kafka-input-topic", "sensebox-measurements",
                                                     "--kafka-output-topic-unparseable", "sensebox-measurements-error-unparseable",
                                                     "--kafka-group-id", "FlinkDbSink",
                                                     "--db-connection-string", "jdbc:postgresql:sbm"))

    val params = paramDefaults.mergeWith(paramCmdline)

    val bootstrapServers = params.get("bootstrap-servers")
    val inputKafkaTopic = params.get("kafka-input-topic")

    logger.info(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    ConnectionPool.singleton(params.get("db-connection-string"), params.get("db-user"), params.get("dbPass"))
    //TODO: setupDb() bzw. Tabellenexistenz prüfen (oder zumindest erkennen, wenn die Tabelle nicht existiert und mit sinnvollem Fehler sterben)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing

    val properties = new Properties {
      put("bootstrap.servers", bootstrapServers)
      put("group.id", params.get("kafka-group-id"))
    }
    val inputStream = env.addSource(new FlinkKafkaConsumer09[String](inputKafkaTopic, new SimpleStringSchema(), properties))

    val jsonStream = inputStream.map(record => deserialize(record))

    val splitStream = jsonStream.split(el => el.keys.contains("error") match {
      case true => List("error")
      case false => List("ok")
    })

    splitStream.select("error").map(el => el toString).addSink(new FlinkKafkaProducer09(params.get("kafka-output-topic-unparseable"), new SimpleStringSchema(), properties))

    val validatedStream = splitStream.select("ok").map(el => validateJson(el))

    //TODO: split(gültig) nach Duplikat/Unikat
    //TODO: Duplikat per Kafka raus

    validatedStream.print()
    //inputStream.addSink(el => saveMeasurement(params, el)) //TODO: nur Unikat

    //TODO: kann man die Source/Sink irgendwie sinnvoll benennen, damit das in der Management Console schöner aussieht?
    env.execute("Sensebox Measurements DB Sink")
  }

  def deserialize(record: String): JsObject = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.deserialize")

    logger.debug(s"Got input '$record'")

    val json: JsObject = try {
      Json.obj(
        "input" -> Json.parse(record)
      )
    } catch {
      case e: Exception => Json.obj(
        "error" -> "Input is not parseable as JSON",
        "rawInput" -> record //TODO: Base64-encoden falls Binärdaten?
      )
    }

    return json
  }

  def validateJson(json: JsObject): JsObject = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.deserialize")

    return json
  }

  def saveMeasurement(params: ParameterTool, ev: ObjectNode): Unit = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.saveMeasurements")

    val boxId = ev.get("boxId").asText
    val sensorId = ev.get("sensor").asText
    val value = ev.get("value").asDouble
    val timestamp = ev.get("createdAt").asText
    val ts = dateFormat.parse(timestamp)
    val sqlTs = new Timestamp(ts.getTime)

    logger.info(s"Got event for $boxId/$sensorId: $value")

    //TODO: Datenbankverbindung hier ggfs. aufbauen. singleton?
    //TODO: Werte prüfen, fehlerhafte loggen (oder in Kafka-Stream?)

    DB.withConnection { implicit connection: Connection =>
      SQL"INSERT INTO measurements(boxid, sensorid, ts, value) VALUES($boxId, $sensorId, $sqlTs, $value)".execute(); //TODO: Fehlerbehandlung
      //TODO: UPSERT/klarkommen, wenn Measurement schon drin weil doppelt ausgeliefert (möglichst auch mit (konfigurierbarem) deltaTimestamp weil das über Codekunst und TTN mit leicht unterschiedlichem TS reinkam)
    }
  }
}