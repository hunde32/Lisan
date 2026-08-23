# 🖐️ Lisan: Smart Glove for Ethiopian Sign Language (ESL)

[![Firmware](https://img.shields.io/badge/Firmware-Bare--Metal%20C-blue)]()
[![Mobile](https://img.shields.io/badge/App-Kotlin%20Android-green)]()
[![Hardware](https://img.shields.io/badge/Hardware-EasyEDA-orange)]()

**Lisan** is an internal engineering project designed to translate Ethiopian Sign Language (ESL) gestures into real-time Amharic text and speech. 

This repository contains the complete end-to-end system documentation and source code. It is structured to help team members and project mentors easily navigate the hardware designs, embedded firmware, and mobile application logic.

---

## 📂 Repository Architecture (Where is What)

The project is divided into three primary domains to keep concerns strictly separated. 

### 1. `/hardware` (Physical & Electrical Design)
Contains all blueprints required to physically build the glove.
* **`/schematics`**: Contains the EasyEDA JSON files (`SCH_Smart-glove_...` and `PCB_smart-glove-pcb_...`). This is where the MCU, MPU6050 (IMU), Bluetooth module, and flex sensor routing are defined.
* **`/mechanical`**: Contains the `Final-Desgin.glb` 3D model used for printing the structural housing of the PCB on the glove.

### 2. `/firmware` (Embedded Systems)
Contains the bare-metal C code responsible for low-latency sensor polling and Bluetooth transmission. 
* **`/src` & `/inc`**: The core drivers. Instead of heavy abstraction layers, we use custom-written implementations for the ADC (flex sensors), I2C (MPU6050), and UART (Bluetooth).
* **`Makefile`**: The build automation script to compile the C code and flash the microcontroller.

### 3. `/mobile-app` (Android Companion)
A native Android application written in Kotlin. It acts as the processing hub, display, and speaker.
* **`/app/src/main/java/.../managers`**: Contains `GloveBluetoothManager.kt` for parsing the serial data stream, and `SpeechManager.kt` for Amharic audio output.
* **`/app/src/main/java/.../ui`**: Contains the Jetpack Compose UI screens and theming.
* **`/app/src/main/java/.../data`**: Contains the local Room database (`AppDatabase.kt`) for mapping raw sensor data to ESL dictionary entries locally, without cloud dependencies.

---

## 🚀 Step-by-Step Setup Guide

For team members and mentors looking to compile, flash, or test the system locally, follow these steps.

### Step 1: Hardware Review & Assembly
1. Open **EasyEDA** on your local machine.
2. Navigate to `hardware/schematics/` and import the schematic JSON to review the logic and pinouts.
3. Import the PCB JSON to inspect the board layout or export the Gerber files.
4. Mount the fabricated PCB, connect the flex sensors, and power the system.

### Step 2: Compiling the Firmware
1. Ensure your embedded toolchain (e.g., `avr-gcc`, `avrdude`) is installed.
2. Open a terminal and navigate to the firmware directory:

    cd firmware

3. Verify the target variables in the `Makefile` match the connected microcontroller.
4. Compile the source code:

    make all

5. Flash the hex binary to the board:

    make flash

### Step 3: Running the Mobile App
1. Open **Android Studio**.
2. Select **Open an existing Project** and target the `mobile-app` directory.
3. Allow Gradle to sync the dependencies (defined in `build.gradle.kts`).
4. Enable **USB Debugging** on a physical Android test device and connect it to the computer.
5. Click **Run** in Android Studio to compile the Kotlin code and install the Lisan APK.
6. Grant Bluetooth permissions in the app and pair it with the glove's hardware module to monitor the live telemetry data.
