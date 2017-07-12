import play.api.libs.json.{JsArray, JsObject}

/**
  * Created by lutz on 12.07.17.
  */
class CodekunstMqttMessageParser(aJson: JsObject) extends MqttMessageParser(aJson) {
  override def getDeviceId(): Option[String] = {
    if (!json.keys.contains("devEUI")) {
      println("Incoming JSON does not contain necessary key 'devEUI'. Ignoring message.")
      return None
    }
    val devEUI = (json \ "devEUI").as[String]

    return Some(devEUI)
  }

  override def getTimestamp(): Option[String] = {
    if (!json.keys.contains("rxInfo")
      || !(json \ "rxInfo").get.isInstanceOf[JsArray]
      || !(json \ "rxInfo")(0).get.isInstanceOf[JsObject]
      || !(json \ "rxInfo")(0).as[JsObject].keys.contains("time")) {
      println("Incoming JSON does not contain necessary key 'rxInfo[0].time'. Ignoring message.")
      return None
    }
    val inputTS = ((json \ "rxInfo")(0) \ "time").as[String]

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
    if (!json.keys.contains("data")) {
      println("Incoming JSON does not contain necessary key 'data'. Ignoring message.")
      return None
    }
    val rawData = (json \ "data").as[String]
    return Some(rawData)
  }
}