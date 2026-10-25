#include "i2c.h"
#include <avr/io.h>

void I2C_Init(void) {
  // Set SCL frequency to 100kHz with 16MHz CPU clock
  // SCL = F_CPU / (16 + 2 * TWBR * 4^TWPS)
  TWSR = 0x00; // Prescaler = 1
  TWBR = 72;   // 16MHz / (16 + 2 * 72) = 100kHz
  TWCR = (1 << TWEN);
}

void I2C_Start(void) {
  TWCR = (1 << TWINT) | (1 << TWSTA) | (1 << TWEN);
  while (!(TWCR & (1 << TWINT)))
    ;
}

void I2C_Stop(void) {
  TWCR = (1 << TWINT) | (1 << TWSTO) | (1 << TWEN);
  while (TWCR & (1 << TWSTO))
    ;
}

void I2C_Write(uint8_t data) {
  TWDR = data;
  TWCR = (1 << TWINT) | (1 << TWEN);
  while (!(TWCR & (1 << TWINT)))
    ;
}

uint8_t I2C_ReadAck(void) {
  TWCR = (1 << TWINT) | (1 << TWEN) | (1 << TWEA);
  while (!(TWCR & (1 << TWINT)))
    ;
  return TWDR;
}

uint8_t I2C_ReadNack(void) {
  TWCR = (1 << TWINT) | (1 << TWEN);
  while (!(TWCR & (1 << TWINT)))
    ;
  return TWDR;
}
