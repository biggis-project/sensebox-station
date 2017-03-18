import java.sql.Connection
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import org.apache.commons.lang3.time.DateUtils

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scalikejdbc.ConnectionPool
import anorm._

import org.slf4j.LoggerFactory
import org.slf4j.Logger

case class Reading(boxId: String, sensor: String, createdAt: String, value: Double)

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
                                                     "--kafka-output-topic-unparseable", "sensebox-measurements-error",
                                                     "--kafka-output-topic-invalid", "sensebox-measurements-error",
                                                     "--kafka-output-topic-duplicate", "sensebox-measurements-error",
                                                     "--kafka-group-id", "FlinkDbSink",
                                                     "--duplicate-filter-interval", "1",
                                                     "--db-connection-string", "jdbc:postgresql:sbm"))

    val params = paramDefaults.mergeWith(paramCmdline)

    val bootstrapServers = params.get("bootstrap-servers")
    val inputKafkaTopic = params.get("kafka-input-topic")

    logger.info(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    ConnectionPool.singleton(params.get("db-connection-string"), params.get("db-user"), params.get("db-pass"))
    //TODO: setupDb() bzw. Tabellenexistenz prüfen (oder zumindest erkennen, wenn die Tabelle nicht existiert und mit sinnvollem Fehler sterben)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing

    val properties = new Properties {
      put("bootstrap.servers", bootstrapServers)
      put("group.id", params.get("kafka-group-id"))
    }
    val inputStream = env.addSource(new FlinkKafkaConsumer09[String](inputKafkaTopic, new SimpleStringSchema(), properties))

    val jsonStream = inputStream.map(record => deserialize(record))

    val parseableSplitStream = jsonStream.split(el => el.keys.contains("error") match {
      case true => List("error")
      case false => List("ok")
    })

    if (params.get("kafka-output-topic-unparseable") != "''")
      parseableSplitStream.select("error").map(el => el toString).addSink(new FlinkKafkaProducer09(bootstrapServers, params.get("kafka-output-topic-unparseable"), new SimpleStringSchema()))

    val validatedStream = parseableSplitStream.select("ok").map(el => validateJson(el))

    val validatedSplitStream = validatedStream.split(el => el.keys.contains("error") match {
      case true => List("error")
      case false => List("ok")
    })

    if (params.get("kafka-output-topic-invalid") != "''")
      validatedSplitStream.select("error").map(el => el toString).addSink(new FlinkKafkaProducer09(bootstrapServers, params.get("kafka-output-topic-invalid"), new SimpleStringSchema()))

    val uniquenessCheckedStream = validatedSplitStream.select("ok").map(el => checkUnique(params, el))

    val uniquenessCheckedSplitStream = uniquenessCheckedStream.split(el => el.keys.contains("error") match {
      case true => List("error")
      case false => List("ok")
    })

    if (params.get("kafka-output-topic-duplicate") != "''")
      uniquenessCheckedSplitStream.select("error").map(el => el toString).addSink(new FlinkKafkaProducer09(bootstrapServers, params.get("kafka-output-topic-duplicate"), new SimpleStringSchema()))

    uniquenessCheckedSplitStream.select("ok").addSink(el => saveMeasurement(params, el))

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
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.validateJson")

    implicit val inputReads: Reads[Reading] = (
      (JsPath \ "boxId").read[String] and
      (JsPath \ "sensor").read[String] and
      (JsPath \ "createdAt").read[String] and
      (JsPath \ "value").read[Double]
    )(Reading.apply _)

    (json \ "input").validate[Reading] match {
      case s: JsSuccess[Reading] => {}
      case e: JsError => return json + ("error" -> JsString("JSON input is missing required field(s)"))
    }

    try {
      val timestamp = (json \ "input" \ "createdAt").as[String]
      dateFormat.parse(timestamp)
    }
    catch {
      case e: Exception => {
        logger.error("Can't parse input field 'createdAt' as JSON Date")
        return json + ("error" -> JsString(s"Can't parse input field 'createdAt' as JSON Date"))
      }
    }

    return json
  }

  def checkUnique(params: ParameterTool, json: JsObject): JsObject = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.checkUnique")

    val input = json \ "input"

    val boxId = (input \ "boxId").as[String]
    val sensorId = (input \ "sensor").as[String]
    val timestamp = (input \ "createdAt").as[String]

    val ts = dateFormat.parse(timestamp)

    val sqlFromTs = new Timestamp(DateUtils.addSeconds(ts, -params.getInt("duplicate-filter-interval")).getTime)
    val sqlToTs   = new Timestamp(DateUtils.addSeconds(ts, params.getInt("duplicate-filter-interval")).getTime)

    val recordExists = try {
      DB.withConnection { implicit connection: Connection =>
        SQL"SELECT 1 AS recordexists FROM measurements WHERE boxid=${boxId} AND sensorid=${sensorId} AND ts BETWEEN ${sqlFromTs} AND ${sqlToTs}".as(SqlParser.int("recordexists").single)
     }
    }
    catch {
      case e: Exception => if (e.getMessage == "SqlMappingError(No rows when expecting a single one)") 0 else logger.error(e.getMessage)
    }

    logger.debug(s"Check returned '$recordExists'")

    val resultJson = recordExists match {
      case 1 => json + ("error" -> JsString("Sensor reading already exists in database"))
      case 0 => json
      case () => json
    }

    return resultJson
  }

  def saveMeasurement(params: ParameterTool, ev: JsObject): Unit = {
    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.saveMeasurements")

    ConnectionPool.singleton(params.get("db-connection-string"), params.get("db-user"), params.get("db-pass"))

    val input = ev \ "input"

    val boxId = (input \ "boxId").as[String]
    val sensorId = (input \ "sensor").as[String]
    val value = (input \ "value").as[Double]
    val timestamp = (input \ "createdAt").as[String]

    val ts = dateFormat.parse(timestamp)
    val sqlTs = new Timestamp(ts.getTime)

    logger.info(s"Got event for $boxId/$sensorId@$ts: $value")

    try {
      DB.withConnection { implicit connection: Connection =>
        SQL"INSERT INTO measurements(boxid, sensorid, ts, value) VALUES($boxId, $sensorId, $sqlTs, $value)".execute();
      }
    }
    catch {
      case e: Exception => logger.error(e.getMessage)
    }
  }
}