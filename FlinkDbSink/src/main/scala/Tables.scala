import java.sql.Timestamp

import slick.driver.PostgresDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}

class Measurements(tag: Tag)
  extends Table[(String, String, Timestamp, Double, Option[String])](tag, "measurements") {

  // this does not follow slick standards: using lower case database labels because Postgres requires quotes on uppercase labels
  def boxid: Rep[String] = column[String]("boxid")
  def sensorid: Rep[String] = column[String]("sensorid")
  def ts: Rep[Timestamp] = column[Timestamp]("ts")
  def value: Rep[Double] = column[Double]("value")
  def location: Rep[Option[String]] = column[Option[String]]("location") //TODO: sinnvollerer Typ (PostGIS?)

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String, Timestamp, Double, Option[String])] =
  (boxid, sensorid, ts, value, location)

  //TODO: pkey(boxid,sensorid,ts)
}
