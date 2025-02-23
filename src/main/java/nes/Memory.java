package nes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Memory {
    private final byte[] ram = new byte[2048]; // 2KB Internal RAM
    private final byte[] prgRom;  // PRG-ROM from cartridge
    private final byte[] saveRam = new byte[8192]; // 8KB Save RAM (Battery-backed)
    private final byte[] expansionRom = new byte[8192]; // Used for some mappers
    private final int[] ppuRegisters = new int[8]; // PPU registers
    private final int[] apuIoRegisters = new int[32]; // APU and I/O registers

    public Memory(String romPath) throws IOException {
        byte[] romData = Files.readAllBytes(Paths.get(romPath));

        // Ensure ROM is valid
        if (romData.length < 16 || romData[0] != 'N' || romData[1] != 'E' || romData[2] != 'S') {
            throw new IOException("Invalid NES ROM file");
        }

        // PRG-ROM Size (16KB units)
        int prgSize = romData[4] * 16 * 1024;
        if (prgSize == 0) {
            throw new IOException("Invalid PRG-ROM size in header");
        }

        // Load PRG-ROM, ensuring mirroring for 16KB vs 32KB ROMs
        prgRom = new byte[prgSize > 16 * 1024 ? prgSize : 32 * 1024]; // Ensure 32KB allocation
        System.arraycopy(romData, 16, prgRom, 0, prgSize);

        // If only 16KB of PRG-ROM is available, mirror it in the second bank
        if (prgSize == 16 * 1024) {
            System.arraycopy(prgRom, 0, prgRom, 16 * 1024, 16 * 1024);
        }

        // Initialize APU/I/O registers with expected default values.
        apuIoRegisters[0x15] = 0xFF; // initialize $4015 to FF
        apuIoRegisters[0x04] = 0xFF; // initialize $4004 to FF
        apuIoRegisters[0x05] = 0xFF; // initialize $4005 to FF
        apuIoRegisters[0x06] = 0xFF; // Initialize $4006 to FF
        apuIoRegisters[0x07] = 0xFF; // Initialize $4007 to FF
    }

    public int read(int addr) {
        int address = addr & 0xFFFF;

        if (address < 0x2000) { // CPU RAM (mirrored every 2KB)
            return ram[address & 0x07FF] & 0xFF;

        } else if (address < 0x4000) { // PPU Registers (mirrored every 8 bytes)
            return ppuRegisters[address & 0x0007];

        } else if (address < 0x4020) { // APU and I/O Registers
            return apuIoRegisters[address - 0x4000];

        } else if (address < 0x6000) { // Expansion ROM (Usually read-only)
            return expansionRom[address - 0x4020] & 0xFF;

        } else if (address < 0x8000) { // Save RAM (Battery-backed)
            return saveRam[address - 0x6000] & 0xFF;

        } else { // PRG-ROM (ROM is **read-only**)
            int prgAddress = (address - 0x8000) & (prgRom.length - 1); // Correct PRG mirroring
            return prgRom[prgAddress] & 0xFF;
        }
    }

    public void write(int addr, int val) {
        int address = addr & 0xFFFF;
        int value = val & 0xFF;

        if (address < 0x2000) { // CPU RAM (Mirrored every 2KB)
            ram[address & 0x07FF] = (byte) value;

        } else if (address < 0x4000) { // PPU Registers (Mirrored)
            ppuRegisters[address & 0x0007] = value;

        } else if (address < 0x4020) { // APU and I/O Registers
            apuIoRegisters[address - 0x4000] = value;

        } else if (address < 0x6000) { // Expansion ROM (Usually read-only)
            System.out.printf("⚠️ Attempted write to Expansion ROM at $%04X (Ignored)\n", address);

        } else if (address < 0x8000) { // Battery-backed Save RAM
            saveRam[address - 0x6000] = (byte) value;

        } else { // 🚨 **PRG-ROM is read-only, block writes**
            System.out.printf("❌ Attempted write to ROM at address: $%04X (Value: %02X)\n", address, value);
        }
    }


}
