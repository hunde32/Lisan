#ifndef UART_H
#define UART_H

#include <stdint.h>

void UART_Init(uint16_t baudrate);
void UART_TxChar(char data);
void UART_TxString(char *str);

#endif
