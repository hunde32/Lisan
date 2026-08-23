#ifndef MPU6050_H
#define MPU6050_H

#include <stdint.h>

#define MPU_ADDR 0xD0 // 0x68 shifted left by 1

void MPU6050_Init(void);
void MPU6050_Read_All(int16_t *ax, int16_t *ay, int16_t *az, int16_t *gx,
                      int16_t *gy, int16_t *gz);

#endif
