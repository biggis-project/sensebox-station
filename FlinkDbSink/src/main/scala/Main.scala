import java.util.Properties

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema

object Main {
  var inputKafkaTopic: String = "sensebox-measurements"
  var bootstrapServers: String = "localhost:9092"

  def main(args: Array[String]) {
    parseOpts(args)

    println(s"Connecting Apache Kafka on $bootstrapServers for topic $inputKafkaTopic")

    //TODO: DB-Verbindung aufbauen
    //TODO: DB-Schema verifizieren? Ggfs. erstellen? Oder nur Fehler werfen?

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val properties = new Properties {
      put("bootstrap.servers", bootstrapServers)
    }
    val stream = env
      .addSource(function = new FlinkKafkaConsumer09[ObjectNode](inputKafkaTopic, new JSONDeserializationSchema(), properties))
//      .map { el => {
//        //saveMeasurement(el)
//        el
//      } }
      //.print
      .addSink(el => saveMeasurement(el))

    env.execute("Sensebox Measurements DB Sink")
  }

  def saveMeasurement(ev: ObjectNode): Unit = {
    val boxId = ev.get("boxId")
    val sensorId = ev.get("sensor")
    val value = ev.get("value")
    val timestamp = ev.get("createdAt")

    //TODO: Werte prüfen, fehlerhafte loggen (oder in Kafka-Stream?)

    //TODO: in DB kippen
    println(s"Könnte jetzt Wert ${value}@${timestamp} für Sensor ${sensorId}@${boxId} speichern.")
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