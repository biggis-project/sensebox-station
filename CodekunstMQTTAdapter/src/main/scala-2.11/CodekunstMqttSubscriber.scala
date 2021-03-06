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

    def getOptionalString(path: String): Option[String] = if (underlying.hasPath(path)) {
      Some(underlying.getString(path))
    } else {
      None
    }
  }

  val config = ConfigFactory.load

  val mode = config.getStringOrDefault("mode", "ttn")
  if (mode == "codekunst") {
    println("Working in Codekunst mode.")
  }
  else if (mode == "ttn") {
    println("Working in TTN mode.")
  }
  else {
    println(s"Unknown mode '$mode'. Terminating.")
    System.exit(1)
  }

  val mqttUrl = config.getStringOrDefault("mqtt.brokerUrl", if (mode=="ttn") "ssl://eu.thethings.network:8883" else "tcp://localhost:1883")
  val mqttTopic = config.getStringOrDefault("mqtt.topic", if (mode=="ttn") "+/devices/+/up" else "application/+/node/+/rx")
  val mqttUser = config.getOptionalString("mqtt.user")
  val mqttPassword = config.getOptionalString("mqtt.password")

  if (mode=="ttn") {
    if (mqttUser.isEmpty) {
      println("Config key 'mqtt.user' is required in ttn mode. Terminating.")
      System.exit(1)
    }

    if (mqttPassword.isEmpty) {
      println("Config key 'mqtt.password' is required in ttn mode. Terminating.")
      System.exit(1)
    }
  }

  val kafkaHost = config.getStringOrDefault("kafka.server", "localhost:9092")
  val kafkaTopic = config.getStringOrDefault("kafka.topic", "sensebox-measurements")
  val kafkaUnifiedTopic = config.getStringOrDefault("kafka.unifiedTopic", "sensebox-measurements-unified")

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
    val mqttConnectOptions: MqttConnectOptions = new MqttConnectOptions()
    if (mode=="ttn") {
      //mqttUser and mqttPassword are validated above to be not None. So no match required here
      mqttConnectOptions.setUserName(mqttUser.get)
      mqttConnectOptions.setPassword(mqttPassword.get.toCharArray)
    }
    client.connect(mqttConnectOptions)

    //Subscribe to Mqtt topic
    client.subscribe(mqttTopic)

    //Callback automatically triggers as and when new message arrives on specified topic
    val callback = new MqttCallback {
      override def messageArrived(topic: String, message: MqttMessage): Unit = {
        println("Received data with topic: '%s', message content: '%s'".format(topic, message))
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

    println(inputJson)

    val parser = if (mode=="ttn") new TtnMqttMessageParser(inputJson) else new CodekunstMqttMessageParser(inputJson)

    val deviceId = parser.getDeviceId() match {
      case Some(e) => e
      case None => {
        println("No device ID found. Ignoring message.")
        return
      }
    }

    val outputTS = parser.getTimestamp() match {
      case Some(e) => e
      case None => {
        println("No timestamp found. Ignoring message.")
        return
      }
    }

    val rawData = parser.getRawData() match {
      case Some(e) => e
      case None => {
        println("No raw data found. Ignoring message.")
        return
      }
    }

    // decodeBuffer liefert Bytes statt Chars, deswegen fixen wir das per map()
    val bytes = base64Decoder.decodeBuffer(rawData).map((i: Byte) => if (i < 0) i + 256 else i)

    val temperature = 1.0/771 * (bytes(1)*256 + bytes(0)) - 18
    val humidity = 1.0/100 * (bytes(3)*256 + bytes(2))
    val pressure = 1.0/81.9187 * (bytes(5)*256 + bytes(4)) + 300
    val temperatureIntern = 1.0/771 * (bytes(7)*256 + bytes(6)) - 18
    val lux = bytes(8) + 255 * (bytes(10)*256 + bytes(9))
    val uv = bytes(11) + 255 * (bytes(13)*256 + bytes(12))

    sendValue(deviceId, outputTS, "temperature", temperature)
    sendValue(deviceId, outputTS, "humidity", humidity)
    sendValue(deviceId, outputTS, "pressure", pressure)
    sendValue(deviceId, outputTS, "temperatureInternal", temperatureIntern)
    sendValue(deviceId, outputTS, "light", lux)
    sendValue(deviceId, outputTS, "uv", uv)

    sendUnifiedMessage(deviceId, outputTS, temperature, humidity, pressure, temperatureIntern, lux, uv)
  }

  def sendValue(boxId: String, timestamp: String, sensor: String, value: Double): Unit = {
    if (kafkaTopic == "")
      return

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

  def sendUnifiedMessage(boxId: String, timestamp: String, temperature: Double, humidity: Double, pressure: Double, temperatureInternal: Double, lux: Double, uv: Double): Unit = {
    if (kafkaUnifiedTopic == "")
      return

    val json = Json.obj(
      "boxId"               -> boxId,
      "createdAt"           -> timestamp,
      "temperature"         -> temperature,
      "humidity"            -> humidity,
      "pressure"            -> pressure,
      "temperatureInternal" -> temperatureInternal,
      "lux"                 -> lux,
      "uv"                  -> uv
    )
    println(json)

    val record = try {
      new ProducerRecord(kafkaUnifiedTopic, "Test", json.toString)
    }
    catch {
      case e: Exception => {
        println(s"Kafka create record error: ${e.getMessage}")
        null
      }
    }

    try {
      val f = kafka.send(record)
      f.get
    }
    catch {
      case e: ExecutionException => {
        println(s"Kafka send execution: ${e.getMessage}")
      }
      case e: Exception => {
        println(s"Kafka send error: ${e.getMessage}")
      }
    }
  }
}
