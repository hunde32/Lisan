# 🔄 Arduino.ino to Bare-Metal AVR C Conversion Guide

This document details the direct conversion of the reference `arduino.ino` sketch from [Smart-Glove-Sign-Language-Translator](https://github.com/WaniaKhance/Smart-Glove-Sign-Language-Translator.git) into deterministic, low-latency bare-metal C targeting the ATmega32 microcontroller.

---

## 📋 Direct Code Comparison

### 1. I2C (Wire.h vs Hardware TWI)

| Arduino (`Wire.h`) | Bare-Metal C (`firmware/src/i2c.c`) | Rationale / Register Mapping |
| :--- | :--- | :--- |
| `Wire.begin();` | `I2C_Init();` | Sets `TWBR = 72` and `TWSR = 0` for 100 kHz SCL clock at 16 MHz $F_{CPU}$, enables TWI via `TWCR = (1 << TWEN)`. |
| `Wire.beginTransmission(0x68);`<br/>`Wire.write(0x6B);`<br/>`Wire.write(0);`<br/>`Wire.endTransmission(true);` | `I2C_Start();`<br/>`I2C_Write(MPU_ADDR);`<br/>`I2C_Write(0x6B);`<br/>`I2C_Write(0x00);`<br/>`I2C_Stop();` | Wakes the MPU-6050 from sleep by writing `0` into the power management register `0x6B`. Direct register polling without C++ class overhead. |
| `Wire.requestFrom(0x68, 14, true);` | `I2C_Start();`<br/>`I2C_Write(MPU_ADDR \| 1);`<br/>`... = read_word_ack();`<br/>`I2C_Stop();` | Sends repeated start and reads registers sequentially with hardware ACK/NACK signaling. |

---

### 2. Analog Sensor Sampling (Flex Pins)

| Arduino (`Wiring.h`) | Bare-Metal C (`firmware/src/adc.c`) | Rationale / Register Mapping |
| :--- | :--- | :--- |
| `analogRead(A0);` | `ADC_Read(0);` | Selects channel 0 in `ADMUX`, triggers conversion via `ADCSRA \|= (1 << ADSC)`, and busy-waits until the hardware flag clears. |
| Overhead of pin lookup table | Direct port channel masking | Eliminates Arduino runtime `digitalPinToPort` table lookups; executes in microsecond timing. |

---

### 3. Serial Communication (Hardware UART)

| Arduino (`HardwareSerial`) | Bare-Metal C (`firmware/src/uart.c`) | Rationale / Register Mapping |
| :--- | :--- | :--- |
| `Serial.begin(9600);` | `UART_Init(9600);` | Computes baud rate divisor `UBRR = (F_CPU / (16 * 9600)) - 1 = 103`, sets `UCSRB = (1 << RXEN) \| (1 << TXEN)`. |
| `Serial.print(...);` | `UART_TxString(buffer);` | Writes bytes directly into `UDR` register while polling `UDRE` (Data Register Empty). |

---

### 4. Telemetry Stream Output

Both firmwares stream the 8-channel comma-separated ASCII string matching the ML model dataset:
```
THUMB,INDEX,MIDDLE,RING,LITTLE,G1,G2,G3\r\n
```
In bare-metal C:
```c
snprintf(buffer, sizeof(buffer), "%u,%u,%u,%u,%u,%d,%d,%d\r\n",
         flex[0], flex[1], flex[2], flex[3], flex[4], ax, ay, az);
UART_TxString(buffer);
```
