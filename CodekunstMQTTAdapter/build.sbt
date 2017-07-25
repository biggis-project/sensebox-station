name := "CodekunstMQTTAdapter"

version := "3.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.eclipse.paho" % "mqtt-client" % "0.4.0"
libraryDependencies += "com.typesafe.play" % "play-json_2.11" % "2.5.12"
libraryDependencies += "org.apache.kafka" % "kafka-clients" % "0.9.0.0"
libraryDependencies += "com.typesafe" % "config" % "1.3.1"

resolvers += "MQTT Repository" at "https://repo.eclipse.org/content/repositories/paho-releases/"
