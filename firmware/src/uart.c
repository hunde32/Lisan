#include "uart.h"
#include <avr/io.h>

void UART_Init(uint16_t baudrate) {
  uint16_t ubrr_value = (F_CPU / 16 / baudrate) - 1;
  UBRRH = (unsigned char)(ubrr_value >> 8);
  UBRRL = (unsigned char)ubrr_value;
  UCSRB = (1 << RXEN) | (1 << TXEN);
  UCSRC = (1 << URSEL) | (1 << UCSZ1) | (1 << UCSZ0);
}
void UART_TxChar(char data) {
  while (!(UCSRA & (1 << UDRE)))
    ;
  UDR = data;
}

void UART_TxString(char *str) {
  while (*str) {
    UART_TxChar(*str++);
  }
}
