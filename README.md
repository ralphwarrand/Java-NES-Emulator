# Java NES Emulator

A Java-based NES emulator built with Maven. This project aims to faithfully recreate the classic Nintendo Entertainment System experience through accurate CPU emulation, video rendering, and audio processing.

## Features

- **CPU Emulation:** Cycle-accurate 6502 CPU behavior (official opcodes + illegal opcodes).
- **PPU Emulation:** Cycle-accurate rendering pipeline, 8x8/8x16 sprites, and hardware scrolling (Loopy's Logic).
- **Mapper Support:**
  - **Mapper 0 (NROM):** Super Mario Bros, Donkey Kong, etc.
  - **Mapper 1 (MMC1):** The Legend of Zelda, Metroid, etc. (Supports Banking & Dynamic Mirroring).
- **Audio:** None (IO Registers mapped but no sound processing yet).
- **Controls:** Keyboard mapping for Player 1.
- **Cross-Platform:** Runs on any system with Java installed.

## Controls

| NES Button | Keyboard Key |
|------------|--------------|
| **A**      | `Z`          |
| **B**      | `X`          |
| **Start**  | `Enter`      |
| **Select** | `Shift`      |
| **D-Pad**  | Arrow Keys   |

## Getting Started

### Prerequisites

- **Java JDK:** Version 17 or higher is recommended.
- **Maven:** For building and managing dependencies.
- **Git:** To clone the repository.

### Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/ralphwarrand/java-nes-emulator.git
   cd java-nes-emulator
   ```
2. **Additional Setup:**
   Ensure you have ROM files to test with. A few resources are included in `/resources`.

### Running the Emulator

For convenience, use the included batch script which handles compilation and execution:

```bash
.\run.bat path_to_rom/rom_name.nes
```
Or for the default test ROM:
```bash
.\run.bat
```

### Example

https://github.com/user-attachments/assets/3d703864-d4fd-41b2-9020-a433d6b10929
