import java.sql.Connection
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema
import org.apache.flink.api.java.utils.ParameterTool

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
    val paramCmdline = ParameterTool.fromArgs(args)
    val paramDefaults = ParameterTool.fromArgs(Array("--bootstrap-servers", "localhost:9092",
                                                     "--input-kafka-topic", "sensebox-measurements",
                                                     "--db-connection-string", "jdbc:postgresql:sbm"))

    val params = paramDefaults.mergeWith(paramCmdline)

    val bootstrapServers = params.get("bootstrap-servers")
    val inputKafkaTopic = params.get("input-kafka-topic")

    val logger = LoggerFactory.getLogger("eu.biggis-project.sensebox-station.flinkDbSink.main")
    logger.info(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    ConnectionPool.singleton(params.get("db-connection-string"), params.get("db-user"), params.get("dbPass"))
    //TODO: setupDb() bzw. Tabellenexistenz prüfen (oder zumindest erkennen, wenn die Tabelle nicht existiert und mit sinnvollem Fehler sterben)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val properties = new Properties {
      put("bootstrap.servers", bootstrapServers)
    }
    val stream = env
      .addSource(function = new FlinkKafkaConsumer09[ObjectNode](inputKafkaTopic, new JSONDeserializationSchema(), properties))
      //TODO: split() nach gültig/ungültig
      //TODO: ungültig per Kafka raus
      //TODO: split(gültig) nach Duplikat/Unikat
      //TODO: Duplikat per Kafka raus
      .addSink(el => saveMeasurement(params, el)) //TODO: nur Unikat

    //TODO: kann man die Source/Sink irgendwie sinnvoll benennen, damit das in der Management Console schöner aussieht?
    env.execute("Sensebox Measurements DB Sink")
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