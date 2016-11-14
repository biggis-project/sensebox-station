package controllers

import java.util.Properties
import javax.inject._

import play.api._
import play.api.mvc._
import org.apache.kafka.clients.producer._
import play.api.libs.json._
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.ExecutionException

@Singleton
class BoxesController @Inject() (configuration: play.api.Configuration) extends Controller {
  val kafkaProps = new Properties()
  kafkaProps.put("bootstrap.servers", configuration.getString("sbsrs.kafkaServer").getOrElse("localhost:9092"))
  kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProps.put("max.block.ms", configuration.getString("sbsrs.kafkaMaxBlockMs").getOrElse("5000"))

  val kafka = try {
    new KafkaProducer[String, String](kafkaProps)
  }
  catch {
    case e: Exception => {
      Logger.error(s"Startup error: ${e.getMessage}")
      null
    }
  }

  val kafkaTopic = configuration.getString("sbsrs.kafkaTopic").getOrElse("sensebox-measurements")

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  /**
    * Create an Action to receive measurements from a SenseBox.
    */
  def saveMeasurement(id: String, sensor: String) = Action(parse.json) { request => //TODO: async?
    val json = request.body
    var res = json.as[JsObject] + ("boxId" -> Json.toJson(id)) +
      ("sensor" -> Json.toJson(sensor))

    if (!res.keys.contains("createdAt")) res += ("createdAt" -> Json.toJson(dateFormat.format(Calendar.getInstance().getTime)))

    val msg = res.toString()

    val value = (request.body \ "value").getOrElse(Json.toJson("/kein Wert übermittelt/")).toString()
    Logger.info(s"Habe Daten für Station '$id', Senser '$sensor': $value")

    val record = try {
      new ProducerRecord(kafkaTopic, "Test", msg)
    }
    catch {
      case e: Exception => {
        Logger.error(s"Kafka error: ${e.getMessage}")
        null
      }
    }

    try {
      val f = kafka.send(record)
      f.get
      Ok("OK.")
    }
    catch {
      case e: ExecutionException => {
        Logger.error(s"Kafka error: ${e.getMessage}")
        InternalServerError(s"Kafka not accessible: ${e.getMessage}")
      }
      case e: Exception => {
        Logger.error(s"Kafka error: ${e.getMessage}")
        InternalServerError(s"Kafka not accessible: ${e.getMessage}")
      }
    }
  }
}
