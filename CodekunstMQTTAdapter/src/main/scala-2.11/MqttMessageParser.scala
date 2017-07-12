import play.api.libs.json.JsObject

/**
  * Created by lutz on 12.07.17.
  */
abstract class MqttMessageParser(aJson: JsObject) {
  val json = aJson

  def getDeviceId(): Option[String]
  def getTimestamp(): Option[String]
  def getRawData(): Option[String]
}
