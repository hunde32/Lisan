#include "adc.h"
#include <avr/io.h>

void ADC_Init(void) {
  ADMUX = (1 << REFS0);
  ADCSRA = (1 << ADEN) | (1 << ADPS2) | (1 << ADPS1) | (1 << ADPS0);
}

uint16_t ADC_Read(uint8_t channel) {
  channel &= 0b00000111;
  ADMUX = (ADMUX & 0xF8) | channel;

  ADCSRA |= (1 << ADSC);

  while (ADCSRA & (1 << ADSC))
    ;

  return ADC;
}
