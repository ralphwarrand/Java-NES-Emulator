# Java NES Emulator

A Java-based NES emulator built with Maven. This project aims to faithfully recreate the classic Nintendo Entertainment System experience through accurate CPU emulation, video rendering, and audio processing.

## Features

- **CPU Emulation:** Accurate reproduction of the 6502 CPU behavior.
- **Graphics & Audio:** Emulation of NES video and sound hardware.
- **ROM Loading:** Load and run your favorite NES game ROMs.
- **Test Driven:** Comprehensive test suite using JUnit for reliable code quality.
- **Cross-Platform:** Runs on any system with Java installed.

## Getting Started

### Prerequisites

- **Java JDK:** Version 17 or higher is recommended.
- **Maven:** For building and managing dependencies.
- **Git:** To clone the repository.

### Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/yourusername/nes-emulator.git
   cd nes-emulator
   ```
2. **Build the project using Maven:**
   
   ```bash
   mvn clean package
   ```

### Runing the Emulator

After building, run the emulator with the following command:
   ```bash
   java -jar target/java-nes-emulator-1.0-SNAPSHOT.jar
   ```
