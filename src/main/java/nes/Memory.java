package nes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import nes.hooks.MemoryHook;

public class Memory {
    // System Memory
    final byte[] ram = new byte[2048]; // 2KB Internal RAM
    private final byte[] saveRam = new byte[8192]; // 8KB Battery Backed RAM

    private final byte[] expansionRom = new byte[8192];

    int openBus = 0; // Last value on data bus

    // Components
    private final int[] apuIoRegisters = new int[32]; // $4000-$401F
    public final byte[] oam = new byte[256]; // (Not used directly here, usually in PPU, but kept for DMA ref)

    // ROM Data
    private byte[] prgRom; // Full PRG Data
    private byte[] chrRom; // Full CHR Data (or RAM)
    private boolean isChrRam = false;

    // Mapper State
    private int mapperID;

    // MMC1 State
    private int currShift = 0;
    private int shiftCount = 0;
    private int mmc1Control = 0x0C; // Default: PRG 16k Mode 3, CHR 8k
    private int mmc1ChrBank0 = 0;
    private int mmc1ChrBank1 = 0;
    private int mmc1PrgBank = 0;

    private PPU ppu;
    private Controller controller1;
    private APU apu;

    public void setAPU(APU apu) {
        this.apu = apu;
    }

    // === Hooks ===
    private final List<MemoryHook> hooks = new ArrayList<>();

    public void addHook(MemoryHook hook) {
        hooks.add(hook);
    }

    public void removeHook(MemoryHook hook) {
        hooks.remove(hook);
    }

    public void clearHooks() {
        hooks.clear();
    }

    public boolean isNmiAsserted() {
        return ppu != null && ppu.nmiOccurred;
    }

    public void consumeNmi() {
        if (ppu != null)
            ppu.nmiOccurred = false;
    }

    public boolean willNmiFire(int cpuCycles) {
        return ppu != null && ppu.willNmiFire(cpuCycles);
    }

    public Memory(String romPath) throws IOException {
        byte[] romData = Files.readAllBytes(Paths.get(romPath));

        if (romData.length < 16 || romData[0] != 'N' || romData[1] != 'E' || romData[2] != 'S') {
            throw new IOException("Invalid NES ROM file");
        }

        // Parse Header
        int prgBanks = romData[4]; // 16KB units
        int chrBanks = romData[5]; // 8KB units
        int control1 = romData[6];
        int control2 = romData[7];

        mapperID = ((control2 & 0xF0) | ((control1 & 0xF0) >> 4));
        System.out.println("Detected Mapper: " + mapperID);

        // Load PRG
        int prgSize = prgBanks * 16384;
        prgRom = new byte[prgSize];
        System.arraycopy(romData, 16, prgRom, 0, prgSize);

        // Load CHR
        if (chrBanks > 0) {
            int chrSize = chrBanks * 8192;
            chrRom = new byte[chrSize];
            System.arraycopy(romData, 16 + prgSize, chrRom, 0, chrSize);
            isChrRam = false;
        } else {
            chrRom = new byte[8192]; // 8KB CHR-RAM
            isChrRam = true;
        }

        // Initialize APU Registers to 0xFF (nestest expects this, likely Open Bus
        // behavior)
        java.util.Arrays.fill(apuIoRegisters, 0xFF);
    }

    public Memory() {
        // Default for testing
        prgRom = new byte[1024];
        chrRom = new byte[1024];
        java.util.Arrays.fill(apuIoRegisters, 0xFF);
    }

    public void setPPU(PPU ppu) {
        this.ppu = ppu;
    }

    public void setController1(Controller controller) {
        this.controller1 = controller;
    }

    // === CPU Memory Map ===

    public int read(int addr) {
        int address = addr & 0xFFFF;
        int value = openBus; // Default to open bus

        if (address < 0x2000) { // RAM
            value = ram[address & 0x07FF] & 0xFF;

        } else if (address < 0x4000) { // PPU
            value = ppu != null ? ppu.readRegister(address & 0x2007, openBus) : openBus;

        } else if (address < 0x4020) { // IO
            if (address == 0x4016)
                value = (openBus & 0xE0) | (controller1 != null ? controller1.read() : 0);
            else if (address == 0x4017)
                value = (openBus & 0xE0) | 0x00; // Controller 2 (Not connected)
            else if (address == 0x4014) // OAMDMA usually Open Bus on read
                value = openBus;
            else {
                // Route to APU
                int val = apu != null ? apu.readRegister(address, openBus) : -1;
                if (val != -1)
                    value = val;
            }

        } else if (address < 0x6000) { // Expansion
            if (mapperID != 0) { // Only read if mapper supports it (MMC1 etc)
                value = expansionRom[address - 0x4020] & 0xFF;
            }

        } else if (address < 0x8000) { // Save RAM
            if (mapperID != 0) {
                value = saveRam[address - 0x6000] & 0xFF;
            }

        } else { // PRG-ROM $8000-$FFFF
            int prg = readPrg(address);
            if (prg != -1)
                value = prg;
        }

        openBus = value; // Bus decay/update

        // Notify Hooks
        if (!hooks.isEmpty()) {
            for (MemoryHook hook : hooks) {
                hook.onRead(addr, value);
            }
        }

        return value;
    }

    public void write(int addr, int val) {
        int address = addr & 0xFFFF;
        int value = val & 0xFF;
        openBus = value; // Bus update (Driver is CPU)

        // Notify Hooks
        if (!hooks.isEmpty()) {
            for (MemoryHook hook : hooks) {
                hook.onWrite(address, value);
            }
        }

        if (address < 0x2000) { // RAM
            ram[address & 0x07FF] = (byte) value;

        } else if (address < 0x4000) { // PPU
            if (ppu != null)
                ppu.writeRegister(address & 0x2007, value);

        } else if (address < 0x4020) { // APU/IO
            if (address == 0x4014) {
                dmaTransfer(value);
                return;
            }
            if (address == 0x4016) {
                if (controller1 != null)
                    controller1.write(value);
                return;
            }

            // Route to APU
            if (apu != null) {
                apu.writeRegister(address, value);
            }

        } else if (address < 0x6000) {
            // Expansion

        } else if (address < 0x8000) {
            saveRam[address - 0x6000] = (byte) value;

        } else {
            // Mapper Writes
            writeMapper(address, value);
        }
    }

    // === Mapper Logic ===

    private int readPrg(int address) {
        // Handle Mapper 0 (NROM)
        if (mapperID == 0) {
            // 32k or 16k mirror
            int mask = (prgRom.length > 16384) ? 0x7FFF : 0x3FFF;
            return prgRom[(address - 0x8000) & mask] & 0xFF;
        }

        // Handle Mapper 1 (MMC1)
        if (mapperID == 1) {
            int bankMode = (mmc1Control >> 2) & 0x03;
            int offset = 0;

            if (bankMode == 0 || bankMode == 1) { // 32KB switching
                // Bank is (mmc1PrgBank & 0xFE)
                int bank = (mmc1PrgBank & 0x0E); // Ignore bit 0
                offset = bank * 32768 + (address - 0x8000);
            } else if (bankMode == 2) { // Fix First @ 8000, Switch 16k @ C000
                if (address < 0xC000) {
                    offset = 0 + (address - 0x8000); // Bank 0 fixed
                } else {
                    int bank = mmc1PrgBank & 0x0F;
                    offset = bank * 16384 + (address - 0xC000);
                }
            } else if (bankMode == 3) { // Fix Last @ C000, Switch 16k @ 8000
                if (address < 0xC000) {
                    int bank = mmc1PrgBank & 0x0F;
                    offset = bank * 16384 + (address - 0x8000);
                } else {
                    // Last Bank Fixed
                    // Total 16k banks = length / 16384
                    int lastBank = (prgRom.length / 16384) - 1;
                    offset = lastBank * 16384 + (address - 0xC000);
                }
            }
            if (offset < prgRom.length)
                return prgRom[offset] & 0xFF;
        }

        return -1;
    }

    private void writeMapper(int address, int value) {
        if (mapperID == 1) {
            // MMC1 Logic
            if ((value & 0x80) != 0) {
                // Reset Shift
                currShift = 0;
                shiftCount = 0;
                mmc1Control |= 0x0C; // Reset control
            } else {
                currShift |= ((value & 0x01) << shiftCount);
                shiftCount++;
                if (shiftCount == 5) {
                    int reg = (address >> 13) & 0x03; // 0=Control, 1=Chr0, 2=Chr1, 3=Prg

                    switch (reg) {
                        case 0: // Control (8000-9FFF)
                            mmc1Control = currShift;
                            int mirror = mmc1Control & 0x03;
                            if (ppu != null) {
                                switch (mirror) {
                                    case 0:
                                        ppu.setMirroring(PPU.MIRROR_ONESCREEN_LO);
                                        break;
                                    case 1:
                                        ppu.setMirroring(PPU.MIRROR_ONESCREEN_HI);
                                        break;
                                    case 2:
                                        ppu.setMirroring(PPU.MIRROR_VERTICAL);
                                        break;
                                    case 3:
                                        ppu.setMirroring(PPU.MIRROR_HORIZONTAL);
                                        break;
                                }
                            }
                            break;
                        case 1: // CHR 0 (A000-BFFF)
                            mmc1ChrBank0 = currShift;
                            updatePPUChrisBanks();
                            break;
                        case 2: // CHR 1 (C000-DFFF)
                            mmc1ChrBank1 = currShift;
                            updatePPUChrisBanks();
                            break;
                        case 3: // PRG (E000-FFFF)
                            mmc1PrgBank = currShift;
                            break;
                    }
                    currShift = 0;
                    shiftCount = 0;
                }
            }
        }
    }

    private void updatePPUChrisBanks() {
        // TODO: determine if needed
    }

    // Helper for PPU to call
    public int readChr(int address) {
        if (mapperID == 0)
            return chrRom[address] & 0xFF;

        if (mapperID == 1) {
            int bankMode = (mmc1Control >> 4) & 0x01;
            int offset = 0;
            if (bankMode == 0) { // 8K Switch
                int bank = mmc1ChrBank0 & 0x1E; // Low bit ignored
                offset = bank * 8192 + address;
            } else { // 4K Switch
                if (address < 0x1000) {
                    offset = mmc1ChrBank0 * 4096 + address;
                } else {
                    offset = mmc1ChrBank1 * 4096 + (address - 0x1000);
                }
            }
            if (offset < chrRom.length)
                return chrRom[offset] & 0xFF;
        }
        return 0;
    }

    public void writeChr(int address, int value) {
        if (mapperID == 0) {
            if (isChrRam)
                chrRom[address] = (byte) value;
            return;
        }

        if (mapperID == 1) {
            int bankMode = (mmc1Control >> 4) & 0x01;
            int offset = 0;
            if (bankMode == 0) { // 8K Switch
                int bank = mmc1ChrBank0 & 0x1E;
                offset = bank * 8192 + address;
            } else { // 4K Switch
                if (address < 0x1000) {
                    offset = mmc1ChrBank0 * 4096 + address;
                } else {
                    offset = mmc1ChrBank1 * 4096 + (address - 0x1000);
                }
            }
            if (isChrRam && offset < chrRom.length) {
                chrRom[offset] = (byte) value;
            }
        }
    }

    private CPU cpu;

    public void setCPU(CPU cpu) {
        this.cpu = cpu;
    }

    private void dmaTransfer(int page) {
        if (cpu != null) {
            cpu.triggerDMA(page);
        }
    }
}
