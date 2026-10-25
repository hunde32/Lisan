#include "mpu6050.h"
#include "i2c.h"
#include <util/delay.h>

void MPU6050_Init(void) {
  I2C_Init();
  _delay_ms(150);
  I2C_Start();
  I2C_Write(MPU_ADDR);
  I2C_Write(0x6B);
  I2C_Write(0x00);
  I2C_Stop();
}

static inline int16_t read_word_ack(void) {
  uint8_t msb = I2C_ReadAck();
  uint8_t lsb = I2C_ReadAck();
  return (int16_t)((msb << 8) | lsb);
}

static inline int16_t read_word_nack(void) {
  uint8_t msb = I2C_ReadAck();
  uint8_t lsb = I2C_ReadNack();
  return (int16_t)((msb << 8) | lsb);
}

void MPU6050_Read_All(int16_t *ax, int16_t *ay, int16_t *az, int16_t *gx,
                      int16_t *gy, int16_t *gz) {
  I2C_Start();
  I2C_Write(MPU_ADDR);
  I2C_Write(0x3B);         // Start from ACCEL_XOUT_H register
  I2C_Start();             // Repeated start for reading
  I2C_Write(MPU_ADDR | 1); // Read mode
  *ax = read_word_ack();
  *ay = read_word_ack();
  *az = read_word_ack();
  I2C_ReadAck();           // Skip temperature MSB
  I2C_ReadAck();           // Skip temperature LSB
  *gx = read_word_ack();
  *gy = read_word_ack();
  *gz = read_word_nack();  // NACK on last byte
  I2C_Stop();
}
