import play.api.libs.json.{JsArray, JsObject}

/**
  * Created by lutz on 12.07.17.
  */
class TtnMqttMessageParser(aJson: JsObject) extends MqttMessageParser(aJson) {
  override def getDeviceId(): Option[String] = {
    if (!json.keys.contains("dev_id")) {
      println("Incoming JSON does not contain necessary key 'dev_id'. Ignoring message.")
      return None
    }
    val devId = (json \ "dev_id").as[String]

    return Some(devId)
  }

  override def getTimestamp(): Option[String] = {
    if (!json.keys.contains("metadata")
      || !(json \ "metadata").get.isInstanceOf[JsObject]
      || !(json \ "metadata").as[JsObject].keys.contains("time")) {
      println("Incoming JSON does not contain necessary key 'metadata.time'. Ignoring message.")
      return None
    }
    val inputTS = ((json \ "metadata") \ "time").as[String]

    val seconds = "\\d{2}\\.\\d+".r findFirstIn inputTS
    val tempTS = seconds match {
      case Some(i) => {
        val roundedSeconds = math.rint(i.toFloat * 1000) / 1000
        inputTS replaceFirst(i, if (roundedSeconds < 10) s"0$roundedSeconds" else s"$roundedSeconds")
      }
      case None => inputTS
    }
    val outputTS = tempTS replaceFirst("Z$", "+0000")

    return Some(outputTS)
  }

  override def getRawData(): Option[String] = {
    if (!json.keys.contains("payload_raw")) {
      println("Incoming JSON does not contain necessary key 'payload_raw'. Ignoring message.")
      return None
    }
    val rawData = (json \ "payload_raw").as[String]
    return Some(rawData)
  }
}
