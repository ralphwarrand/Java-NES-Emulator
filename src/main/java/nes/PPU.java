package nes;

import nes.gui.Display;

public class PPU {
    // Registers (CPU Visible)
    private int ctrl; // $2000
    private int mask; // $2001
    private int status; // $2002
    private int oamAddr; // $2003

    // Loopy's Internal Registers (Scrolling)
    private int v; // Current VRAM address (15 bit)
    private int t; // Temporary VRAM address (15 bit)
    private int x; // Fine X Scroll (3 bit)
    private int w; // Write latch (0 or 1)

    private int bufferData; // Internal buffer for $2007 reads

    // Cycle Management
    private int cycle = 0;
    private int scanline = 0; // 0-261
    public boolean nmiOccurred = false;
    public boolean frameComplete = false;

    // Background Rendering Pipeline (Latches & Shifters)
    private int bgNextTileId;
    private int bgNextTileAttrib;
    private int bgNextTileLsb;
    private int bgNextTileMsb;

    private int bgShifterPatternLo;
    private int bgShifterPatternHi;
    private int bgShifterAttribLo;
    private int bgShifterAttribHi;

    // Memory
    public final byte[] nametables = new byte[2048]; // 2KB VRAM
    public final byte[] paletteRam = new byte[32];
    public final byte[] oam = new byte[256]; // Object Attribute Memory

    private final Display display;

    // Palette
    private static final int[] PALETTE_LOOKUP = {
            0x545454, 0x001E74, 0x081090, 0x300088, 0x440064, 0x5C0030, 0x540400, 0x3C1800,
            0x202A00, 0x083A00, 0x004000, 0x003C00, 0x00323C, 0x000000, 0x000000, 0x000000,
            0x989698, 0x084CC4, 0x3032EC, 0x5C1E14, 0x8814B0, 0xA01464, 0x982220, 0x783C00,
            0x545A00, 0x287200, 0x087C00, 0x007628, 0x006678, 0x000000, 0x000000, 0x000000,
            0xECEEEC, 0x4C9AEC, 0x787CEC, 0xB062EC, 0xE454EC, 0xEC58B4, 0xEC6A64, 0xD48820,
            0xA0AA00, 0x74C400, 0x4CD020, 0x38CC6C, 0x38B4CC, 0x3C3C3C, 0x000000, 0x000000,
            0xECEEEC, 0xA8CCEC, 0xBCBCEC, 0xD4B2EC, 0xECAEEC, 0xECAED4, 0xECB4B0, 0xE4C490,
            0xCCD278, 0xB4DE78, 0xA8E290, 0x98E2B4, 0xA0D6E4, 0xA0A2A0, 0x000000, 0x000000
    };

    public PPU(Display display) {
        this.display = display;
    }

    // === Register Interfaces ===

    public int readRegister(int addr) {
        switch (addr) {
            case 0x2000:
                return 0; // Write only
            case 0x2001:
                return 0; // Write only
            case 0x2002:
                // Reading Status resets Write Latch and clears VBlank
                int res = (status & 0xE0) | (bufferData & 0x1F); // Noise from buffer
                status &= ~0x80;
                w = 0;
                return res;
            case 0x2003:
                return 0; // Write only
            case 0x2004:
                return oam[oamAddr] & 0xFF; // Usually not readable
            case 0x2005:
                return 0; // Write only
            case 0x2006:
                return 0; // Write only
            case 0x2007:
                int data = bufferData;
                bufferData = readVram(v);

                // If address is in Palette range, it returns immediately (no buffer delay)
                if (v >= 0x3F00)
                    data = bufferData;

                v += ((ctrl & 0x04) != 0) ? 32 : 1;
                return data;
        }
        return 0;
    }

    public void writeRegister(int addr, int val) {
        switch (addr) {
            case 0x2000: // Control
                ctrl = val;
                // Update NameTable select (t: ...GH..)
                t = (t & 0xF3FF) | ((val & 0x03) << 10);
                break;
            case 0x2001: // Mask
                mask = val;
                break;
            case 0x2003: // OAM Addr
                oamAddr = val;
                break;
            case 0x2004: // OAM Data
                oam[oamAddr] = (byte) val;
                oamAddr = (oamAddr + 1) & 0xFF;
                break;
            case 0x2005: // Scroll
                if (w == 0) {
                    // First Write: Fine X and Coarse X
                    x = val & 0x07;
                    t = (t & 0xFFE0) | ((val >> 3) & 0x1F);
                    w = 1;
                } else {
                    // Second Write: Fine Y and Coarse Y
                    t = (t & 0x8FFF) | ((val & 0x07) << 12);
                    t = (t & 0xFC1F) | ((val & 0xF8) << 2);
                    w = 0;
                }
                break;
            case 0x2006: // Address
                if (w == 0) {
                    // First Write: High Byte
                    t = (t & 0x00FF) | ((val & 0x3F) << 8); // Bit 14 cleared
                    w = 1;
                } else {
                    // Second Write: Low Byte and Update v
                    t = (t & 0xFF00) | val;
                    v = t;
                    w = 0;
                }
                break;
            case 0x2007: // Data
                writeVram(v, val);
                v += ((ctrl & 0x04) != 0) ? 32 : 1;
                break;
        }
    }

    // === Execution ===

    public void tick() {
        // --- Background Logic ---
        if (scanline >= 0 && scanline < 240 || scanline == 261) { // Visible or Pre-render
            if (scanline == 0 && cycle == 0 && frameComplete) {
                frameComplete = false; // Start new frame
            }

            if ((mask & 0x18) != 0) { // If rendering enabled
                // Cycle-based fetching
                if ((cycle >= 2 && cycle < 258) || (cycle >= 321 && cycle < 338)) {
                    updateShifters();

                    switch ((cycle - 1) % 8) {
                        case 0:
                            loadBackgroundShifters();
                            // Fetch NT Byte
                            bgNextTileId = readVram(0x2000 | (v & 0x0FFF));
                            break;
                        case 2:
                            // Fetch Attribute Byte
                            // Complex address calc: 0x23C0 + (v.nt << 10) + ((v.y >> 5) << 3) + (v.x >> 5)
                            // But v has specific layout: yyy NN YYYYY XXXXX
                            int addr = 0x23C0 | (v & 0x0C00) | ((v >> 4) & 0x38) | ((v >> 2) & 0x07);
                            bgNextTileAttrib = readVram(addr);
                            // Process Quad
                            if ((v & 0x0040) != 0)
                                bgNextTileAttrib >>= 4; // Top/Bottom
                            if ((v & 0x0002) != 0)
                                bgNextTileAttrib >>= 2; // Left/Right
                            bgNextTileAttrib &= 0x03;
                            break;
                        case 4:
                            // Fetch Lo BG
                            int tableAddr = ((ctrl & 0x10) != 0) ? 0x1000 : 0x0000;
                            bgNextTileLsb = readVram(tableAddr + (bgNextTileId * 16) + ((v >> 12) & 0x07));
                            break;
                        case 6:
                            // Fetch Hi BG
                            int tableAddr2 = ((ctrl & 0x10) != 0) ? 0x1000 : 0x0000;
                            bgNextTileMsb = readVram(tableAddr2 + (bgNextTileId * 16) + ((v >> 12) & 0x07) + 8);
                            break;
                        case 7:
                            incrementScrollX();
                            break;
                    }
                }

                // Vertical Increment
                if (cycle == 256) {
                    incrementScrollY();
                }

                // Horizontal Reset
                if (cycle == 257) {
                    transferAddressX();
                }

                // Vertical Reset (Pre-render only)
                if (scanline == 261 && cycle >= 280 && cycle < 305) {
                    transferAddressY();
                }
            } // End rendering enabled check
        }

        // --- Rendering ---
        if (scanline == 240) {
            // Post-render scanline (idle)
        }

        // VBlank
        if (scanline == 241 && cycle == 1) {
            status |= 0x80;
            if ((ctrl & 0x80) != 0)
                nmiOccurred = true;
            display.refresh();
        }

        // Pre-render clear flags
        if (scanline == 261 && cycle == 1) {
            status &= ~(0x80 | 0x40 | 0x20); // Clear VBlank, Sprite 0, Overflow
            nmiOccurred = false;
        }

        // Pixel Output (Visible Area)
        if (scanline < 240 && cycle > 0 && cycle <= 256) {
            renderPixel();
        }

        // --- End of Cycle ---
        cycle++;
        if (cycle >= 341) {
            cycle = 0;
            scanline++;
            if (scanline >= 262) {
                scanline = 0;
                frameComplete = true; // Signal Main Loop
            }
        }
    }

    // === Logic Helpers ===

    private void renderPixel() {
        int bgPixel = 0;
        int bgPalette = 0;

        // 1. Background
        if ((mask & 0x08) != 0) {
            int bitMux = 0x8000 >> x;

            int p0 = (bgShifterPatternLo & bitMux) != 0 ? 1 : 0;
            int p1 = (bgShifterPatternHi & bitMux) != 0 ? 1 : 0;
            bgPixel = (p1 << 1) | p0;

            int pal0 = (bgShifterAttribLo & bitMux) != 0 ? 1 : 0;
            int pal1 = (bgShifterAttribHi & bitMux) != 0 ? 1 : 0;
            bgPalette = (pal1 << 1) | pal0;
        }

        // 2. Sprites
        int sprPixel = 0;
        int sprPalette = 0;
        boolean sprPriority = true;
        boolean sprite0HitPossible = false;

        if ((mask & 0x10) != 0) {
            for (int i = 0; i < 64; i++) {
                int index = i * 4;
                int sy = oam[index] & 0xFF; // Y Position
                int height = ((ctrl & 0x20) != 0) ? 16 : 8;
                int diffY = scanline - sy;

                if (diffY >= 0 && diffY < height) {
                    int id = oam[index + 1] & 0xFF;
                    int attr = oam[index + 2] & 0xFF;
                    int sx = oam[index + 3] & 0xFF;

                    int diffX = (cycle - 1) - sx;
                    if (diffX >= 0 && diffX < 8) {
                        // Pixel exists
                        boolean flipH = (attr & 0x40) != 0;
                        boolean flipV = (attr & 0x80) != 0;

                        // Select tile row
                        int tileRow = diffY;
                        if (flipV)
                            tileRow = height - 1 - tileRow;

                        // Pattern Address
                        int patternAddr;
                        if (height == 8) {
                            patternAddr = ((ctrl & 0x08) != 0 ? 0x1000 : 0x0000) + id * 16 + tileRow;
                        } else {
                            // 8x16
                            patternAddr = ((id & 0x01) * 0x1000) + ((id & 0xFE) * 16) + tileRow;
                            if (tileRow >= 8)
                                patternAddr += 8; // Fix: Jump 8 bytes to get to bottom tile (since tileRow is already
                                                  // +8)
                        }

                        // Optimize: Don't read VRAM inside loop if avoidable, but for now simple
                        int lo = readVram(patternAddr);
                        int hi = readVram(patternAddr + 8);

                        int bit = flipH ? diffX : (7 - diffX);
                        int val = ((lo >> bit) & 1) | (((hi >> bit) & 1) << 1);

                        if (val != 0) {
                            // We found a sprite pixel (priority handled by order of iteration break)
                            if (sprPixel == 0) {
                                sprPixel = val;
                                sprPalette = (attr & 0x03) + 4;
                                sprPriority = (attr & 0x20) == 0; // 0: Front
                                if (i == 0)
                                    sprite0HitPossible = true;
                            }
                            break; // Stop at first opaque sprite pixel
                        }
                    }
                }
            }
        }

        // 3. Sprite 0 Hit
        if (sprite0HitPossible && bgPixel != 0) {
            // Hardware requires rendering enabled and x != 255
            if ((mask & 0x18) == 0x18 && (cycle - 1) != 255) {
                status |= 0x40;
            }
        }

        // 4. Multiplexer
        int finalPixel;
        int finalPalette;

        if (bgPixel == 0 && sprPixel == 0) {
            // Background Color (Universal)
            finalPixel = 0;
            finalPalette = 0;
        } else if (bgPixel == 0 && sprPixel > 0) {
            finalPixel = sprPixel;
            finalPalette = sprPalette;
        } else if (bgPixel > 0 && sprPixel == 0) {
            finalPixel = bgPixel;
            finalPalette = bgPalette;
        } else {
            // Both
            if (sprPriority) {
                finalPixel = sprPixel;
                finalPalette = sprPalette;
            } else {
                finalPixel = bgPixel;
                finalPalette = bgPalette;
            }
        }

        int colorIndex = readVram(0x3F00 + (finalPalette << 2) + finalPixel);
        display.setPixel(cycle - 1, scanline, PALETTE_LOOKUP[colorIndex & 0x3F]);
    }

    // === Shifters & Scrolling ===

    private void updateShifters() {
        if ((mask & 0x08) != 0) {
            bgShifterPatternLo <<= 1;
            bgShifterPatternHi <<= 1;
            bgShifterAttribLo <<= 1;
            bgShifterAttribHi <<= 1;
        }
    }

    private void loadBackgroundShifters() {
        bgShifterPatternLo = (bgShifterPatternLo & 0xFF00) | bgNextTileLsb;
        bgShifterPatternHi = (bgShifterPatternHi & 0xFF00) | bgNextTileMsb;

        // Expand Attribute bits to 8-bit width
        bgShifterAttribLo = (bgShifterAttribLo & 0xFF00) | ((bgNextTileAttrib & 0x01) != 0 ? 0xFF : 0x00);
        bgShifterAttribHi = (bgShifterAttribHi & 0xFF00) | ((bgNextTileAttrib & 0x02) != 0 ? 0xFF : 0x00);
    }

    private void incrementScrollX() {
        if ((mask & 0x18) != 0) {
            if ((v & 0x001F) == 31) {
                v &= ~0x001F; // Clear Coarse X
                v ^= 0x0400; // Switch Horizontal Nametable
            } else {
                v++;
            }
        }
    }

    private void incrementScrollY() {
        if ((mask & 0x18) != 0) {
            int fineY = (v & 0x7000) >> 12;
            if (fineY < 7) {
                fineY++;
                v = (v & ~0x7000) | (fineY << 12);
            } else {
                v &= ~0x7000; // Reset fine Y
                int y = (v & 0x03E0) >> 5;
                if (y == 29) {
                    y = 0;
                    v ^= 0x0800; // Switch Vertical Nametable
                } else if (y == 31) {
                    y = 0;
                } else {
                    y++;
                }
                v = (v & ~0x03E0) | (y << 5);
            }
        }
    }

    private void transferAddressX() {
        if ((mask & 0x18) != 0) {
            v = (v & ~0x041F) | (t & 0x041F);
        }
    }

    private void transferAddressY() {
        if ((mask & 0x18) != 0) {
            v = (v & ~0x7BE0) | (t & 0x7BE0);
        }
    }

    private Memory memory;

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    // === VRAM Access ===
    // Mirroring Modes
    public static final int MIRROR_HORIZONTAL = 0;
    public static final int MIRROR_VERTICAL = 1;
    public static final int MIRROR_ONESCREEN_LO = 2;
    public static final int MIRROR_ONESCREEN_HI = 3;

    private int mirroring = MIRROR_VERTICAL; // Default

    public void setMirroring(int mode) {
        this.mirroring = mode;
    }

    public int readVram(int addr) {
        int address = addr & 0x3FFF;
        if (address < 0x2000) {
            // Pattern Tables (Delegated to Memory for Mapper Banking)
            return memory != null ? memory.readChr(address) : 0;
        } else if (address < 0x3F00) {
            // Nametables
            int index = getMirroredAddress(address) & 0x07FF;
            return nametables[index] & 0xFF;
        } else if (address < 0x4000) {
            // Palettes
            address &= 0x001F;
            if (address == 0x10)
                address = 0x00;
            if (address == 0x14)
                address = 0x04;
            if (address == 0x18)
                address = 0x08;
            if (address == 0x1C)
                address = 0x0C;
            return paletteRam[address] & 0xFF;
        }
        return 0;
    }

    public void writeVram(int addr, int val) {
        int address = addr & 0x3FFF;
        if (address < 0x2000) {
            // Pattern Tables
            if (memory != null)
                memory.writeChr(address, val);
        } else if (address < 0x3F00) {
            int index = getMirroredAddress(address) & 0x07FF;
            nametables[index] = (byte) val;
        } else if (address < 0x4000) {
            address &= 0x001F;
            if (address == 0x10)
                address = 0x00;
            if (address == 0x14)
                address = 0x04;
            if (address == 0x18)
                address = 0x08;
            if (address == 0x1C)
                address = 0x0C;
            paletteRam[address] = (byte) val;
        }
    }

    private int getMirroredAddress(int addr) {
        int address = addr & 0x0FFF; // 0x0000 - 0x0FFF offset
        int table = address / 0x400; // 0, 1, 2, 3

        switch (mirroring) {
            case MIRROR_HORIZONTAL:
                // [0] [0]
                // [1] [1]
                if (table == 1)
                    address -= 0x400; // Map 1->0
                if (table == 2)
                    address -= 0x400; // Map 2->1
                if (table == 3)
                    address -= 0x800; // Map 3->1
                break;
            case MIRROR_VERTICAL:
                // [0] [1]
                // [0] [1]
                if (table == 2)
                    address -= 0x800; // Map 2->0
                if (table == 3)
                    address -= 0x800; // Map 3->1
                break;
            case MIRROR_ONESCREEN_LO:
                address &= 0x03FF; // Always Table 0
                break;
            case MIRROR_ONESCREEN_HI:
                address = (address & 0x03FF) + 0x400; // Always Table 1
                break;
        }
        return address;
    }
}
