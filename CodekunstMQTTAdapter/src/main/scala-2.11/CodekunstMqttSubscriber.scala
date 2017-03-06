import java.util.Properties
import java.util.concurrent.ExecutionException

import com.typesafe.config.{Config, ConfigFactory}
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import play.api.libs.json._
import org.apache.kafka.clients.producer._
import org.slf4j.Logger

/**
  * Created by Jochen Lutz on 22.02.17 using template from https://github.com/mqtt/mqtt.github.io/wiki/Scala-MQTT-Client
  */

object CodekunstMqttSubscriber {
  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getStringOrDefault(path: String, default: String): String = if (underlying.hasPath(path)) {
      underlying.getString(path)
    } else {
      default
    }
  }

  val config = ConfigFactory.load

  val mqttUrl = config.getStringOrDefault("mqtt.brokerUrl", "tcp://localhost:1883")
  val mqttTopic = config.getStringOrDefault("mqtt.topic", "application/+/node/+/rx")

  val kafkaHost = config.getStringOrDefault("kafka.server", "localhost:9092")
  val kafkaTopic = config.getStringOrDefault("kafka.topic", "sensebox-measurements")

  val base64Decoder = new sun.misc.BASE64Decoder()

  val kafkaProps = new Properties()
  kafkaProps.put("bootstrap.servers", kafkaHost)
  kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProps.put("max.block.ms", "5000")

  val kafka = try {
    new KafkaProducer[String, String](kafkaProps)
  }
  catch {
    case e: Exception => {
      println(s"Kafka startup error: ${e.getMessage}")
      null
    }
  }

  def main(args: Array[String]) {
    //Set up persistence for messages
    val persistence = new MemoryPersistence

    println(s"Connecte zu MQTT-Broker ${mqttUrl} mit Topic '${mqttTopic}'")
    //Logger.info(s"Connecte zu MQTT-Broker ${mqttUrl} mit Topic '${mqttTopic}'")
    //Initializing Mqtt Client specifying brokerUrl, clientID and MqttClientPersistance
    val client = new MqttClient(mqttUrl, MqttClient.generateClientId, persistence)

    //Connect to MqttBroker
    client.connect

    //Subscribe to Mqtt topic
    client.subscribe(mqttTopic)

    //Callback automatically triggers as and when new message arrives on specified topic
    val callback = new MqttCallback {
      override def messageArrived(topic: String, message: MqttMessage): Unit = {
        println("Receiving Data, Topic : %s, Message : %s".format(topic, message))
        parseMessage(message)
      }

      override def connectionLost(cause: Throwable): Unit = {
         println(cause)
       }

      override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
    }

    //Set up callback for MqttClient
    client.setCallback(callback)
  }

  def parseMessage(message: MqttMessage): Unit = {
    val inputJson: JsObject = try {
      Json.parse(message.toString).as[JsObject]
    }
    catch {
      case e: Exception => {
        println(e)
        return
      }
    }

    if (!inputJson.keys.contains("devEUI")) {
      println("Incoming JSON does not contain necessary key 'devEUI'. Ignoring message.")
      return
    }
    val devEUI = (inputJson \ "devEUI").as[String]

    if (!inputJson.keys.contains("rxInfo")
      || !(inputJson \ "rxInfo").get.isInstanceOf[JsArray]
      || !(inputJson \ "rxInfo")(0).get.isInstanceOf[JsObject]
      || !(inputJson \ "rxInfo")(0).as[JsObject].keys.contains("time")) {
      println("Incoming JSON does not contain necessary key 'rxInfo[0].time'. Ignoring message.")
      return
    }
    val inputTS = ((inputJson \ "rxInfo")(0) \ "time").as[String]

    if (!inputJson.keys.contains("data")) {
      println("Incoming JSON does not contain necessary key 'data'. Ignoring message.")
      return
    }
    val rawData = (inputJson \ "data").as[String]
    println(inputJson)

    // decodeBuffer liefert Bytes statt Chars, deswegen fixen wir das per map()
    val bytes = base64Decoder.decodeBuffer(rawData).map((i: Byte) => if (i < 0) i + 256 else i)

    val temperature = 1.0/771 * (bytes(1)*256 + bytes(0)) - 18
    val humidity = 1.0/100 * (bytes(3)*256 + bytes(2))
    val pressure = 1.0/81.9187 * (bytes(5)*256 + bytes(4)) + 300
    val temperatureIntern = 1.0/771 * (bytes(7)*256 + bytes(6)) - 18
    val lux = bytes(8) + 255 * (bytes(10)*256 + bytes(9))
    val uv = bytes(11) + 255 * (bytes(13)*256 + bytes(12))

    sendValue(devEUI, inputTS, "temperature", temperature)
    sendValue(devEUI, inputTS, "humidity", humidity)
    sendValue(devEUI, inputTS, "pressure", pressure)
    sendValue(devEUI, inputTS, "temperatureInternal", temperatureIntern)
    sendValue(devEUI, inputTS, "light", lux)
    sendValue(devEUI, inputTS, "uv", uv)
  }

  def sendValue(boxId: String, timestamp: String, sensor: String, value: Double): Unit = {
    val json = Json.obj("boxId" -> boxId, "createdAt" -> timestamp, "sensor" -> sensor, "value" -> value)
    println(json)

    val record = try {
      new ProducerRecord(kafkaTopic, "Test", json.toString)
    }
    catch {
      case e: Exception => {
        println(s"Kafka error: ${e.getMessage}")
        null
      }
    }

    try {
      val f = kafka.send(record)
      f.get
    }
    catch {
      case e: ExecutionException => {
        println(s"Kafka error: ${e.getMessage}")
      }
      case e: Exception => {
        println(s"Kafka error: ${e.getMessage}")
      }
    }
  }
}
