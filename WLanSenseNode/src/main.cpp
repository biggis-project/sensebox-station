#include <Arduino.h>
#include <SPI.h> //include explicitly, dependency management does not handle this
#include <WiFi101.h>

//Custom WiFi Parameters (never commit to Git!)
char ssid[] = "...";      //  your network SSID (name)
char pass[] = "...";      // your network password

void wifiConnect() {
  if (WiFi.status() == WL_NO_SHIELD) {
    Serial.println(F("WiFi shield not present. Stopping."));
    while (true); //stop!
  }

  int status = WL_IDLE_STATUS;
  int maxTries = 10;
  while ((status != WL_CONNECTED) && (maxTries-- > 0)) {
    Serial.print(F("Attempting to connect to WPA SSID: ")); Serial.println(ssid);
    status = WiFi.begin(ssid, pass);

    delay(6000);
  }

  if (status != WL_CONNECTED) {
    Serial.print(F("Can't connect to SSID ")); Serial.print(ssid); Serial.println(F(". Stopping."));
    while (true); //stop!
  }
}

void readSensors() {
}

void sendData() {
}

void setup() {
  Serial.begin(115200);

  delay(2000);
  Serial.print("Starting up ...");

  wifiConnect();
}

void loop() {
}
