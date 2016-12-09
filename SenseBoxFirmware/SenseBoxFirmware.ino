/*
  senseBox Citizen Sensingplatform
  WiFi Version: 1.0.1
  Date: 2016-08-21
  Homepage: http://www.sensebox.de
  Author: Institute for Geoinformatics, University of Muenster
  Note: Sketch for SB-Home WiFi Edition
  Code is in the public domain.
*/

//#include <avr/wdt.h>
#include "BMP280.h"
#include <Wire.h>
#include <HDC100X.h>
#include <SPI.h>
#include <WiFi101.h>
#include <Makerblog_TSL45315.h>

//Custom WiFi Parameters
char ssid[] = "...";      //  your network SSID (name)
char pass[] = "...";      // your network password

//Network settings
char server[] = "ipe-koi09.perimeter.fzi.de";
int serverPort = 9000;
int status = WL_IDLE_STATUS;
WiFiClient client;

//Sensor Instances
Makerblog_TSL45315 TSL = Makerblog_TSL45315(TSL45315_TIME_M4);
HDC100X HDC(0x43);
BMP280 BMP;

//measurement variables
#define UV_ADDR 0x38
#define IT_1   0x1
float temperature = 0;
float humidity = 0;
double tempBaro, pressure;
char result;

//senseBox ID
#define SENSEBOX_ID "e72d2433-3bf5-4673-9110-df2619d15991"

//Sensor IDs
#define TEMPSENSOR_ID "3d50986c"
#define HUMISENSOR_ID "91e84b36"
#define PRESSURESENSOR_ID "9b2197b8"
#define LUXSENSOR_ID "a5b3f303"
#define UVSENSOR_ID "3c99fc59"

void setup() {
  //Initialize serial and wait for port to open:
  Serial.begin(9600);

  while (!Serial) {
    ; // wait for serial port to connect. Needed for native USB port only
  }

  delay(5000);//Arduino.SerialMonitor will nicht in VBox. Zeit um picocom zu aktivieren

  Serial.println("Serial connected. Hello you.");

  //Enable Wifi Shield
  pinMode(4, INPUT);
  digitalWrite(4, HIGH);
  //Check WiFi Shield status
  if (WiFi.status() == WL_NO_SHIELD) {
    Serial.println("WiFi shield not present");
    // don't continue:
    while (true);
  }
  // attempt to connect to Wifi network:
  while ( status != WL_CONNECTED) {
    Serial.print("Attempting to connect to SSID: ");
    Serial.println(ssid);
    // Connect to WPA/WPA2 network. Change this line if using open or WEP network
    status = WiFi.begin(ssid, pass);
    // wait 6 seconds for connection:
    Serial.println();
    Serial.print("Waiting 6 seconds for connection...");
    delay(6000);
    Serial.println("done.");
  }
  Serial.print("Connected with IP address ");
  Serial.println(WiFi.localIP());
  Serial.print("Initializing sensors...");
  Wire.begin();
  Wire.beginTransmission(UV_ADDR);
  Wire.write((IT_1 << 2) | 0x02);
  Wire.endTransmission();
  delay(500);
  HDC.begin(HDC100X_TEMP_HUMI, HDC100X_14BIT, HDC100X_14BIT, DISABLE);
  TSL.begin();
  BMP.begin();
  BMP.setOversampling(4);
  Serial.println("done!");
  Serial.println("Starting loop.");
  temperature = HDC.getTemp();
}

void loop() {
  httpRequest(TEMPSENSOR_ID, String(HDC.getTemp()));
  delay(1000);
  httpRequest(HUMISENSOR_ID, String(HDC.getHumi()));
  delay(1000);
  result = BMP.startMeasurment();
  if (result != 0) {
    delay(result);
    result = BMP.getTemperatureAndPressure(tempBaro, pressure);
  }
  delay(1000);
  httpRequest(PRESSURESENSOR_ID, String(pressure));
  delay(1000);
  httpRequest(LUXSENSOR_ID, String(TSL.readLux()));
  delay(1000);
  httpRequest(UVSENSOR_ID, String(getUV()));
  Serial.println("===");
  delay(15000);
}

void httpRequest(String sensorId, String value) {
  String valueJson = "{\"value\":";
  valueJson += value;
  valueJson += "}";
  // close any connection before send a new request.
  // This will free the socket on the WiFi shield
  if (client.connected()) {
    client.stop();
  }
  // if there's a successful connection:
  if (client.connect(server, serverPort)) {
    Serial.print("connecting ... ");
    Serial.print(sensorId);
    Serial.print("=");
    Serial.print(value);
    // send the HTTP PUT request:
    client.print("POST /boxes/");
    client.print(SENSEBOX_ID);
    client.print("/");
    client.print(sensorId);
    client.println(" HTTP/1.1");
    // Send the required header parameters
    client.print("Host: ");
    client.print(server);
    client.print(":");
    client.println(serverPort);
    client.println("Content-Type: application/json");
    client.println("Connection: close");
    client.print("Content-Length: ");
    client.println(valueJson.length());
    client.println();
    client.print(valueJson);
    client.println();
    Serial.println(" ==> done!");
    client.stop();
  }
  else {
    // if you couldn't make a connection:
    Serial.println("connection failed. Restarting System.");
    delay(5000);
    asm volatile (" jmp 0");
  }
}

uint16_t getUV() {
  byte msb = 0, lsb = 0;
  uint16_t uvValue;
  Wire.requestFrom(UV_ADDR + 1, 1); //MSB
  delay(1);
  if (Wire.available()) msb = Wire.read();
  Wire.requestFrom(UV_ADDR + 0, 1); //LSB
  delay(1);
  if (Wire.available()) lsb = Wire.read();
  uvValue = (msb << 8) | lsb;
  return uvValue * 5;
}

