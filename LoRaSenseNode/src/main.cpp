/*
 * Firmware von BigGIS LoRa Sensor Nodes
 *
 * Based on LoRa firmware by Gerald Pape (TODO: Copyright etc.)
 */

#include <LoraMessage.h>

#include <Wire.h>
#include "Adafruit_HDC1000.h"
#include "Adafruit_BMP280.h"
#include <Makerblog_TSL45315.h>
#include <SPI.h>

#include <lmic.h>
#include <hal/hal.h>

#include <avr/wdt.h>

//Load sensors
Makerblog_TSL45315 tsl = Makerblog_TSL45315(TSL45315_TIME_M4);
Adafruit_HDC1000 hdc = Adafruit_HDC1000();
Adafruit_BMP280 bmp;

//measurement variables
float temperature = 0;
float humidity = 0;
double tempBaro = 0, pressure = 0;
uint32_t lux = 0;
uint16_t uv = 0;
#define UV_ADDR 0x38
#define IT_1   0x1

#ifndef AppEUI
#error "You must provide AppEUI as compile time define (e.g. -DAppEUI='{ 0xF3, 0xE4, 0xC5, 0xB6, 0xA7, 0x98, 0x89, 0x9A }')."
#endif
static const u1_t PROGMEM tmpAppEUI[8] = AppEUI;
void os_getArtEui (u1_t* buf) {
  memcpy_P(buf, tmpAppEUI, 8);
}

#ifndef DevEUI
#error "You must provide DevEUI as compile time define (e.g. -DDevEUI='{ 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88 }')."
#endif
static const u1_t PROGMEM tmpDevEUI[8] = DevEUI;
void os_getDevEui (u1_t* buf) {
  memcpy_P(buf, tmpDevEUI, 8);
}

#ifndef AppKey
#error "You must provide AppKey as compile time define (e.g. -DAppKey='{ 0x0F, 0x1E, 0x2D, 0x3C, 0x4B, 0x5A 0x69, 0x78, 0x87, 0x96, 0xA5, 0xB4, 0xC3, 0xD2, 0xE1, 0xF0 }')."
#endif
static const u1_t PROGMEM tmpAppKey[16] = AppKey;
void os_getDevKey (u1_t* buf) {
  memcpy_P(buf, tmpAppKey, 16);
}

static osjob_t sendjob;

// Schedule TX every this many seconds (might become longer due to duty
// cycle limitations).
#ifndef TX_INTERVAL
#define TX_INTERVAL 600
#endif

// Pin mapping for Dragino LoRa Shield V1.3/V1.4
const lmic_pinmap lmic_pins = {
  .nss = 10,
  .rxtx = LMIC_UNUSED_PIN,
  .rst = 9,
  .dio = {2, 6, 7}
};

void reboot () {
  Serial.println("Restarting System.");
  delay(5000);
  asm volatile (" jmp 0");
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

void do_send(osjob_t* j) {
  // Check if there is not a current TX/RX job running
  if (LMIC.opmode & OP_TXRXPEND) {
    Serial.println(F("OP_TXRXPEND, not sending"));
  } else {
    LoraMessage message;

    //-----Temperature-----//
    Serial.print("temperature: ");
    temperature = hdc.readTemperature();
    Serial.println(temperature);
    message.addUint16((temperature + 18) * 771); //damit können wir -18 - +67 °C abbilden
    delay(2000);

    //-----Humidity-----//
    Serial.print(F("humidity: "));
    humidity = hdc.readHumidity();
    Serial.println(humidity);
    message.addHumidity(humidity);
    delay(2000);

    //-----Pressure-----//
    Serial.print("pressure: ");
    pressure = bmp.readPressure() / 100;
    Serial.println(pressure);
    message.addUint16((pressure - 300) * 81.9187);
    
    Serial.print(F("internal temp: "));
    tempBaro = bmp.readTemperature();
    Serial.println(tempBaro);
    message.addUint16((tempBaro + 18) * 771);
    delay(2000);

    //-----Lux-----//
    Serial.print("illuminance: ");
    lux = tsl.readLux();
    Serial.println(lux);
    message.addUint8(lux % 255);
    message.addUint16(lux / 255);
    delay(2000);

    //UV intensity
    Serial.print("uv: ");
    uv = getUV();
    Serial.println(uv);
    message.addUint8(uv % 255);
    message.addUint16(uv / 255);

    // Prepare upstream data transmission at the next possible time.
    LMIC_setTxData2(1, message.getBytes(), message.getLength(), 0);
    Serial.println(F("Packet queued"));
  }
  // Next TX is scheduled after TX_COMPLETE event.
}

void onEvent (ev_t ev) {
  Serial.print(os_getTime());
  Serial.print(": ");
  switch (ev) {
    case EV_SCAN_TIMEOUT:
      Serial.println(F("EV_SCAN_TIMEOUT"));
      break;
    case EV_BEACON_FOUND:
      Serial.println(F("EV_BEACON_FOUND"));
      break;
    case EV_BEACON_MISSED:
      Serial.println(F("EV_BEACON_MISSED"));
      break;
    case EV_BEACON_TRACKED:
      Serial.println(F("EV_BEACON_TRACKED"));
      break;
    case EV_JOINING:
      Serial.println(F("EV_JOINING"));
      break;
    case EV_JOINED:
      Serial.println(F("EV_JOINED"));

      // Disable link check validation (automatically enabled
      // during join, but not supported by TTN at this time).
      LMIC_setLinkCheckMode(0);
      break;
    case EV_RFU1:
      Serial.println(F("EV_RFU1"));
      break;
    case EV_JOIN_FAILED:
      Serial.println(F("EV_JOIN_FAILED"));
      reboot();
      break;
    case EV_REJOIN_FAILED:
      Serial.println(F("EV_REJOIN_FAILED"));
      reboot();
      break;
      break;
    case EV_TXCOMPLETE:
      Serial.println(F("EV_TXCOMPLETE (includes waiting for RX windows)"));
      if (LMIC.txrxFlags & TXRX_ACK)
        Serial.println(F("Received ack"));
      if (LMIC.dataLen) {
        Serial.println(F("Received "));
        Serial.println(LMIC.dataLen);
        Serial.println(F(" bytes of payload"));
      }
      // Schedule next transmission
      os_setTimedCallback(&sendjob, os_getTime() + sec2osticks(TX_INTERVAL), do_send);
      break;
    case EV_LOST_TSYNC:
      Serial.println(F("EV_LOST_TSYNC"));
      reboot();
      break;
    case EV_RESET:
      Serial.println(F("EV_RESET"));
      reboot();
      break;
    case EV_RXCOMPLETE:
      // data received in ping slot
      Serial.println(F("EV_RXCOMPLETE"));
      break;
    case EV_LINK_DEAD:
      Serial.println(F("EV_LINK_DEAD"));
      break;
    case EV_LINK_ALIVE:
      Serial.println(F("EV_LINK_ALIVE"));
      break;
    default:
      Serial.println(F("Unknown event"));
      break;
  }
}

void initSensors() {
  Serial.println(F("Initializing sensors..."));
  Wire.begin();
  Wire.beginTransmission(UV_ADDR);
  Wire.write((IT_1 << 2) | 0x02);
  Wire.endTransmission();
  Serial.println("CP1");
  delay(500);


  hdc.begin(0x43);
  Serial.println("CP1.1");
  hdc.readTemperature(); // weil erstes Reading Müll ist
  hdc.readHumidity(); // same
  Serial.println("CP2");

  tsl.begin();
  Serial.println("CP3");

  if (!bmp.begin())
    Serial.println(F("Failure initializing BMP280"));

  Serial.println(F("done!"));
  Serial.println(F("Starting loop."));
}

void setup() {
  Serial.begin(115200);
  Serial.println(F("Starting BigGIS Sensor Node ..."));

  pinMode(4, INPUT);
  digitalWrite(4, HIGH);

  // LMIC init
  os_init();
  LMIC_reset();
  LMIC.txpow = 27;
  LMIC.datarate = DR_SF12;

  initSensors();

  // Start job (sending automatically starts OTAA too)
  do_send(&sendjob);
}

void loop() {
  os_runloop_once();
}
