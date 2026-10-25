#include <avr/io.h>
#include "adc.h"
#include "mpu6050.h"
#include "uart.h"
#include <stdio.h>
#include <util/delay.h>

    int main(void) {
  ADC_Init();
  UART_Init(9600);
  MPU6050_Init();

  DDRA &= ~((1 << PA0) | (1 << PA1) | (1 << PA2) | (1 << PA3) | (1 << PA4));

  int16_t ax, ay, az, gx, gy, gz;
  uint16_t flex[5];
  char buffer[128];

  while (1) {
    for (int i = 0; i < 5; i++) {
      flex[i] = ADC_Read(i);
    }
    MPU6050_Read_All(&ax, &ay, &az, &gx, &gy, &gz);

    snprintf(buffer, sizeof(buffer), "%u,%u,%u,%u,%u,%d,%d,%d,%d,%d,%d\r\n",
             flex[0], flex[1], flex[2], flex[3], flex[4], ax, ay, az, gx, gy,
             gz);
    UART_TxString(buffer);

    _delay_ms(50);
  }

  return 0;
}
