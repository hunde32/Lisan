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

void MPU6050_Read_All(int16_t *ax, int16_t *ay, int16_t *az, int16_t *gx,
                      int16_t *gy, int16_t *gz) {
  I2C_Start();
  I2C_Write(MPU_ADDR);
  I2C_Write(0x3B);         // Start from ACCEL_XOUT_H register
  I2C_Start();             // Repeated start for reading
  I2C_Write(MPU_ADDR | 1); // Read mode
  *ax = (I2C_ReadAck() << 8) | I2C_ReadAck();
  *ay = (I2C_ReadAck() << 8) | I2C_ReadAck();
  *az = (I2C_ReadAck() << 8) | I2C_ReadAck();
  I2C_ReadAck();
  I2C_ReadAck();
  *gx = (I2C_ReadAck() << 8) | I2C_ReadAck();
  *gy = (I2C_ReadAck() << 8) | I2C_ReadAck();
  *gz = (I2C_ReadAck() << 8) | I2C_ReadNack(); // NACK on last byte
  I2C_Stop();
}
