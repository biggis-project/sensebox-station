package controllers

import java.util.Properties
import javax.inject._

import play.api._
import play.api.mvc._
import org.apache.kafka.clients.producer._
import play.api.libs.json._
import java.text.SimpleDateFormat
import java.util.Calendar

@Singleton
class BoxesController @Inject() (configuration: play.api.Configuration) extends Controller {
  val kafkaProps = new Properties()
  kafkaProps.put("bootstrap.servers", configuration.getString("sbsrs.kafkaServer").getOrElse("localhost:9092"))
  kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val kafka = new KafkaProducer[String, String](kafkaProps)

  val kafkaTopic = configuration.getString("sbsrs.kafkaTopic").getOrElse("sensebox-measurements")

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  /**
    * Create an Action to receive measurements from a SenseBox.
    */
  def saveMeasurement(id: String, sensor: String) = Action(parse.json) { request => //TODO: async?
    val json = request.body
    var res = json.as[JsObject] + ("boxId" -> Json.toJson(id)) +
      ("sensor" -> Json.toJson(sensor))

    if (!res.keys.contains("createdAt")) {
      res += ("createdAt" -> Json.toJson(dateFormat.format(Calendar.getInstance().getTime)))
    }

    val msg = res.toString()

    val value = (request.body \ "value").getOrElse(Json.toJson("/kein Wert übermittelt/")).toString()
    Logger.info(s"Habe Daten für Station '$id', Senser '$sensor': $value")

    val record = new ProducerRecord(kafkaTopic, "Test", msg)
    val f = kafka.send(record) //TODO: auswerten, ggfs. 500 (Internal Server Error) zurückgeben
    Ok("OK.")
  }
}
