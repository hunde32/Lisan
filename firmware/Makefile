MCU = atmega32
F_CPU = 16000000UL
BAUD = 9600

CC = avr-gcc
OBJCOPY = avr-objcopy
CFLAGS = -Wall -g -Os -mmcu=$(MCU) -DF_CPU=$(F_CPU) -Iinc
LDFLAGS = 

SRC = src/main.c src/adc.c src/uart.c src/i2c.c src/mpu6050.c
OBJ = $(SRC:.c=.o)

TARGET = build/glove

PROGRAMMER = usbasp
PORT = /dev/ttyACM0

all: $(TARGET).hex

$(TARGET).elf: $(OBJ)
	@mkdir -p build
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $^

$(TARGET).hex: $(TARGET).elf
	$(OBJCOPY) -O ihex -R .eeprom $< $@

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

flash: $(TARGET).hex
	avrdude -c $(PROGRAMMER) -p $(MCU) -P $(PORT) -U flash:w:$(TARGET).hex:i

clean:
	rm -rf build $(OBJ)
