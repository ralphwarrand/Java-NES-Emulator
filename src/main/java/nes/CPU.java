package nes;

public class CPU {

    // === Definitions
    // =================================================================================================

    private static final int STACK_START = 0xFD; // Default NES stack start ($01FD)

    public static final String[] OP_NAMES = {
            "BRK", "ORA", "KIL", "SLO", "NOP", "ORA", "ASL", "SLO", "PHP", "ORA", "ASL", "ANC", "NOP", "ORA", "ASL",
            "SLO",
            "BPL", "ORA", "KIL", "SLO", "NOP", "ORA", "ASL", "SLO", "CLC", "ORA", "NOP", "SLO", "NOP", "ORA", "ASL",
            "SLO",
            "JSR", "AND", "KIL", "RLA", "BIT", "AND", "ROL", "RLA", "PLP", "AND", "ROL", "ANC", "BIT", "AND", "ROL",
            "RLA",
            "BMI", "AND", "KIL", "RLA", "NOP", "AND", "ROL", "RLA", "SEC", "AND", "NOP", "RLA", "NOP", "AND", "ROL",
            "RLA",
            "RTI", "EOR", "KIL", "SRE", "NOP", "EOR", "LSR", "SRE", "PHA", "EOR", "LSR", "ALR", "JMP", "EOR", "LSR",
            "SRE",
            "BVC", "EOR", "KIL", "SRE", "NOP", "EOR", "LSR", "SRE", "CLI", "EOR", "NOP", "SRE", "NOP", "EOR", "LSR",
            "SRE",
            "RTS", "ADC", "KIL", "RRA", "NOP", "ADC", "ROR", "RRA", "PLA", "ADC", "ROR", "ARR", "JMP", "ADC", "ROR",
            "RRA",
            "BVS", "ADC", "KIL", "RRA", "NOP", "ADC", "ROR", "RRA", "SEI", "ADC", "NOP", "RRA", "NOP", "ADC", "ROR",
            "RRA",
            "NOP", "STA", "NOP", "SAX", "STY", "STA", "STX", "SAX", "DEY", "NOP", "TXA", "XAA", "STY", "STA", "STX",
            "SAX",
            "BCC", "STA", "KIL", "AHX", "STY", "STA", "STX", "SAX", "TYA", "STA", "TXS", "TAS", "SHY", "STA", "SHX",
            "AHX",
            "LDY", "LDA", "LDX", "LAX", "LDY", "LDA", "LDX", "LAX", "TAY", "LDA", "TAX", "LAX", "LDY", "LDA", "LDX",
            "LAX",
            "BCS", "LDA", "KIL", "LAX", "LDY", "LDA", "LDX", "LAX", "CLV", "LDA", "TSX", "LAS", "LDY", "LDA", "LDX",
            "LAX",
            "CPY", "CMP", "NOP", "DCP", "CPY", "CMP", "DEC", "DCP", "INY", "CMP", "DEX", "AXS", "CPY", "CMP", "DEC",
            "DCP",
            "BNE", "CMP", "KIL", "DCP", "NOP", "CMP", "DEC", "DCP", "CLD", "CMP", "NOP", "DCP", "NOP", "CMP", "DEC",
            "DCP",
            "CPX", "SBC", "NOP", "ISB", "CPX", "SBC", "INC", "ISB", "INX", "SBC", "NOP", "SBC", "CPX", "SBC", "INC",
            "ISB",
            "BEQ", "SBC", "KIL", "ISB", "NOP", "SBC", "INC", "ISB", "SED", "SBC", "NOP", "ISB", "NOP", "SBC", "INC",
            "ISB"
    };

    public static final int[] OP_CYCLES = {
            7, 6, 0, 8, 3, 3, 5, 5, 3, 2, 2, 0, 4, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
            6, 6, 0, 8, 3, 3, 5, 5, 4, 2, 2, 0, 4, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
            6, 6, 0, 8, 3, 3, 5, 5, 3, 2, 2, 0, 3, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 0, 4, 2, 7, 4, 4, 7, 7,
            6, 6, 0, 8, 3, 3, 5, 5, 4, 2, 2, 0, 5, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
            2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
            2, 6, 0, 6, 4, 4, 4, 4, 2, 5, 2, 5, 5, 5, 5, 5,
            2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
            2, 5, 0, 5, 4, 4, 4, 4, 2, 4, 2, 4, 4, 4, 4, 4,
            2, 6, 2, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
            2, 6, 2, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
            2, 5, 0, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7
    };

    // === CPU Components
    // ==============================================================================================

    private int registers; // Register bitfield (32-bit integer)
    private int PC; // 16-bit Program Counter
    private int flags; // 8-bit Status Flags
    private long totalCycles = 0; // Total CPU cycles executed
    private boolean loggingEnabled = false;

    private final Memory memory;

    // Helper for correct timing on page crosses
    private boolean pageBoundaryCrossed = false;

    // Flag bit positions
    private static final int FLAG_C = 0; // Carry
    private static final int FLAG_Z = 1; // Zero
    private static final int FLAG_I = 2; // Interrupt Disable
    private static final int FLAG_D = 3; // Decimal Mode
    private static final int FLAG_B = 4; // Break Command
    private static final int FLAG_V = 6; // Overflow
    private static final int FLAG_N = 7; // Negative

    // === CPU Functions
    // ===============================================================================================

    public CPU(Memory mem) {
        this.memory = mem;
        reset();
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    public void reset(Integer startPC) {
        if (startPC != null) {
            PC = startPC;
        } else {
            PC = (memory.read(0xFFFC) & 0xFF) | ((memory.read(0xFFFD) & 0xFF) << 8);
        }

        flags = 0x24; // IRQ Disabled (I=1), Unused Bit (5) always set
        setSP(STACK_START); // Set stack pointer using bitfield
        totalCycles = 7; // Initialization takes 7 cycles
    }

    public void reset() { // Default NES CPU behaviour
        reset(null); // Calls the version with an argument, defaulting to the reset vector
    }

    public long getTotalCycles() {
        return totalCycles;
    }

    private void branch(boolean condition) {
        if (condition) {
            totalCycles++; // Branch taken penalty
            int rawOffset = memory.read(PC + 1); // Fetch offset byte

            int basePC = PC + 2; // The PC after reading the branch instruction
            int signedOffset = (rawOffset > 0x7F) ? rawOffset - 0x100 : rawOffset;
            int newPC = basePC + signedOffset;

            if ((basePC & 0xFF00) != (newPC & 0xFF00)) {
                totalCycles++; // Page crossing penalty
            }

            PC = newPC;
        } else {
            PC += 2; // Just skip the instruction
        }
    }

    // === Register Getters and Setters
    // ================================================================================
    private int getA() {
        return registers & 0xFF;
    }

    private int getX() {
        return (registers >> 8) & 0xFF;
    }

    private int getY() {
        return (registers >> 16) & 0xFF;
    }

    private int getSP() {
        return (registers >> 24) & 0xFF;
    }

    private void setA(int value) {
        registers = (registers & 0xFFFFFF00) | (value & 0xFF);
    }

    private void setX(int value) {
        registers = (registers & 0xFFFF00FF) | ((value & 0xFF) << 8);
    }

    private void setY(int value) {
        registers = (registers & 0xFF00FFFF) | ((value & 0xFF) << 16);
    }

    private void setSP(int value) {
        registers = (registers & 0x00FFFFFF) | ((value & 0xFF) << 24);
    }

    // === Memory Location
    // =============================================================================================

    private int getImmediate() {
        return memory.read(PC + 1);
    }

    private int getZeroPage() {
        return memory.read(memory.read(PC + 1) & 0xFF);
    }

    private int getAbsolute() {
        int addr = memory.read(PC + 1) | (memory.read(PC + 2) << 8);
        return memory.read(addr);
    }

    private int getAbsoluteX() {
        int low = memory.read(PC + 1); // Fetch the low byte
        int high = memory.read(PC + 2); // Fetch the high byte
        int baseAddr = low | (high << 8); // Combine into a 16-bit address

        int effectiveAddr = (baseAddr + getX()) & 0xFFFF; // Add X register, ensure 16-bit wraparound

        pageBoundaryCrossed = (baseAddr & 0xFF00) != (effectiveAddr & 0xFF00);

        return memory.read(effectiveAddr); // Fetch and return the value at the computed address
    }

    private int getAbsoluteY() {
        int low = memory.read(PC + 1); // Fetch the low byte
        int high = memory.read(PC + 2); // Fetch the high byte
        int baseAddr = low | (high << 8); // Combine into a 16-bit address

        int effectiveAddr = (baseAddr + getY()) & 0xFFFF; // Add Y register, handle 16-bit wraparound

        pageBoundaryCrossed = (baseAddr & 0xFF00) != (effectiveAddr & 0xFF00);

        return memory.read(effectiveAddr);
    }

    private int getZeroPageX() {
        int addr = (memory.read(PC + 1) + getX()) & 0xFF; // Wrap around 0x00-0xFF
        return memory.read(addr);
    }

    private int getZeroPageY() {
        int addr = (memory.read(PC + 1) + getY()) & 0xFF; // Wrap around 0x00-0xFF
        return memory.read(addr);
    }

    private int getIndirectX() {
        int pointer = (memory.read(PC + 1) + getX()) & 0xFF; // Wrap at 0xFF
        int lo = memory.read(pointer);
        int hi = memory.read((pointer + 1) & 0xFF); // Wrap at 0xFF
        return (hi << 8) | lo; // Combine into 16-bit address
    }

    private int getIndirectY() {
        int zpAddr = memory.read(PC + 1) & 0xFF; // Read operand
        int baseAddr = memory.read(zpAddr) | (memory.read((zpAddr + 1) & 0xFF) << 8); // Fetch pointer (little-endian)
        int effectiveAddr = (baseAddr + getY()) & 0xFFFF; // Add Y register, handle 16-bit wraparound

        pageBoundaryCrossed = (baseAddr & 0xFF00) != (effectiveAddr & 0xFF00);

        return effectiveAddr;
    }

    // === Flag Handling
    // ===============================================================================================
    private boolean getFlag(int bit) {
        return (flags & (1 << bit)) != 0;
    }

    private void setFlag(int bit, boolean value) {
        if (value)
            flags |= (1 << bit);
        else
            flags &= ~(1 << bit);
    }

    private void setFlagsFromByte(int value) {
        flags = value & 0xFF; // Keep within 8-bit range
        flags &= ~0x10; // Ensure B flag (Bit 4) is cleared
        flags |= 0x20; // Ensure Bit 5 (Unused) is always set
    }

    private void setZeroAndNegativeFlags(int value) {
        setFlag(FLAG_Z, value == 0);
        setFlag(FLAG_N, (value & 0x80) != 0);
    }

    // === Helpers
    // =====================================================================================================
    private void pushStack(int value) {
        memory.write(0x0100 + getSP(), value & 0xFF); // Write to stack memory
        setSP((getSP() - 1) & 0xFF); // Decrement SP and wrap in 8-bit range
    }

    private int popStack() {
        setSP((getSP() + 1) & 0xFF); // Increment SP and wrap in 8-bit range
        return memory.read(0x0100 + getSP()) & 0xFF; // Read from stack memory
    }

    private int getFirstOperand() {
        return (PC + 1 < 0x10000) ? memory.read(PC + 1) & 0xFF : 0;
    }

    private int getSecondOperand() {
        return (PC + 2 < 0x10000) ? memory.read(PC + 2) & 0xFF : 0;
    }

    private int fetchOperandValue(String mode) { // Fetch the operand value based on the addressing mode
        return switch (mode) {
            case "Immediate" -> getImmediate();
            case "ZeroPage" -> getZeroPage();
            case "ZeroPageY" -> getZeroPageY();
            case "ZeroPageX" -> getZeroPageX();
            case "Absolute" -> getAbsolute();
            case "AbsoluteX" -> getAbsoluteX();
            case "AbsoluteY" -> getAbsoluteY();
            case "IndirectX" -> memory.read(getIndirectX());
            case "IndirectY" -> memory.read(getIndirectY());
            default -> {
                System.out.printf("Unknown addressing mode for operand fetch: %s\n", mode);
                yield 0;
            }
        };
    }

    private int getEffectiveAddress(String mode) { // Compute the effective address (for read/write operations)
        // Note: For Read-Modify-Write instructions or Stores, we don't usually add
        // cycle penalties
        // in the same way, or the calling switch case handles it.
        // However, we still need to set pageBoundaryCrossed for potential checks.
        int addr = 0;
        int baseAddr = 0;
        switch (mode) {
            case "ZeroPage":
                addr = memory.read(PC + 1) & 0xFF;
                break;
            case "ZeroPageX":
                addr = (memory.read(PC + 1) + getX()) & 0xFF;
                break;
            case "ZeroPageY":
                addr = (memory.read(PC + 1) + getY()) & 0xFF;
                break;
            case "Absolute":
                addr = (memory.read(PC + 1) | (memory.read(PC + 2) << 8)) & 0xFFFF;
                break;
            case "AbsoluteX":
                baseAddr = memory.read(PC + 1) | (memory.read(PC + 2) << 8);
                addr = (baseAddr + getX()) & 0xFFFF;
                pageBoundaryCrossed = (baseAddr & 0xFF00) != (addr & 0xFF00);
                break;
            case "AbsoluteY":
                baseAddr = memory.read(PC + 1) | (memory.read(PC + 2) << 8);
                addr = (baseAddr + getY()) & 0xFFFF;
                pageBoundaryCrossed = (baseAddr & 0xFF00) != (addr & 0xFF00);
                break;
            case "IndirectX":
                addr = getIndirectX();
                break;
            case "IndirectY":
                addr = getIndirectY();
                break;
        }
        return addr;
    }

    // === Logging
    // =====================================================================================================
    private void printInstructionLog(int opcode, String instruction, String mode, int instructionSize) {
        if (!loggingEnabled)
            return;

        int effectiveAddr = -1, finalValue = 0;
        int baseAddr = -1;

        // Detect unofficial opcodes (NOP, LAX, etc.)
        boolean isUnofficial = switch (opcode) {
            case 0x04, 0x44, 0x64, // ZeroPage DOP variants
                    0x0C, 0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4, // TOP variants (Absolute/ZeroPage,X)
                    0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA, // one-byte NOPs (Implied)
                    0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC, // Absolute,X unofficial NOPs
                    0x80, // Immediate NOP (unofficial)
                    0xA3, 0xA7, 0xAF, 0xB3, 0xB7, 0xBF, // Unofficial LAX
                    0x83, 0x87, 0x8F, 0x97, // Unofficial SAX
                    0xE3, 0xE7, 0xEF, 0xF3, 0xF7, 0xFB, 0xFF, // Unofficial ISB
                    0xEB, // Unofficial SBC
                    0xC3, 0xC7, 0xCF, 0xD3, 0xD7, 0xDB, 0xDF, // Unofficial DCP
                    0x03, 0x07, 0x0F, 0x13, 0x17, 0x1B, 0x1F, 0x23, 0x27, 0x2F, 0x33, 0x37, 0x3B, 0x3F, // Unofficial
                                                                                                        // SLO
                    0x43, 0x47, 0x4F, 0x53, 0x57, 0x5B, 0x5F, // Unofficial SRE
                    0x67, 0x63, 0x6F, 0x73, 0x7B, 0x7F, // Unofficial RRE
                    0x77 // Unofficial RRA
                -> true;
            default -> false;
        };

        // Determine the effective address for memory reads (only for logging purposes,
        // doesn't affect flags)
        switch (mode) {
            case "ZeroPage", "ZeroPageX", "ZeroPageY":
                effectiveAddr = (getFirstOperand() +
                        (mode.contains("X") ? getX() : mode.contains("Y") ? getY() : 0)) & 0xFF;
                break;
            case "Absolute", "AbsoluteX", "AbsoluteY":
                baseAddr = (getSecondOperand() << 8) | getFirstOperand();
                effectiveAddr = (baseAddr + (mode.contains("X") ? getX() : mode.contains("Y") ? getY() : 0)) & 0xFFFF;
                break;
            case "IndirectX":
                effectiveAddr = getIndirectX();
                break;
            case "IndirectY":
                // Re-calculate for logging (don't update flag here)
                int zpAddr = memory.read(PC + 1) & 0xFF;
                baseAddr = memory.read(zpAddr) | (memory.read((zpAddr + 1) & 0xFF) << 8);
                effectiveAddr = (baseAddr + getY()) & 0xFFFF;
                break;
        }

        // If the instruction reads from memory, fetch its value.
        if (effectiveAddr != -1) {
            finalValue = memory.read(effectiveAddr);
        }

        boolean isMemoryRead = instruction
                .matches("LD[AXY]|BIT|CMP|AND|SBC|ORA|EOR|ADC|CP[XY]|LSR|ASL|ROR|ROL|INC|DEC");
        boolean isMemoryWrite = instruction.startsWith("ST");

        // Format the operand description (this is used for display after the mnemonic)
        String operandDesc = switch (mode) {
            case "Accumulator" -> "A";
            case "Immediate" -> String.format("#$%02X", getFirstOperand());
            case "ZeroPage" ->
                String.format("$%02X%s%s", getFirstOperand(),
                        mode.contains("X") ? ",X" : mode.contains("Y") ? ",Y" : "",
                        (isMemoryRead || isMemoryWrite) ? String.format(" = %02X", finalValue) : "");
            case "ZeroPageX", "ZeroPageY" -> {
                int addr = (getFirstOperand() + (mode.contains("X") ? getX() : getY())) & 0xFF;
                yield String.format("$%02X,%s @ %02X = %02X",
                        getFirstOperand(),
                        mode.contains("X") ? "X" : "Y",
                        addr,
                        finalValue);
            }
            case "Absolute" ->
                String.format("$%04X%s%s", baseAddr,
                        mode.contains("X") ? ",X" : mode.contains("Y") ? ",Y" : "",
                        (isMemoryRead || isMemoryWrite) ? String.format(" = %02X", finalValue) : "");
            case "AbsoluteX" ->
                String.format("$%04X,X @ %04X = %02X", baseAddr, effectiveAddr, finalValue);
            case "AbsoluteY" ->
                String.format("$%04X,Y @ %04X = %02X", baseAddr, effectiveAddr, finalValue);
            case "Indirect" -> {
                int indirectAddr = (getSecondOperand() << 8) | getFirstOperand();
                int targetAddr = (memory.read((indirectAddr & 0xFF00) | ((indirectAddr + 1) & 0xFF)) << 8)
                        | memory.read(indirectAddr);
                yield String.format("($%04X) = %04X", indirectAddr, targetAddr);
            }
            case "IndirectX" -> String.format("($%02X,X) @ %02X = %04X = %02X",
                    getFirstOperand(), (getFirstOperand() + getX()) & 0xFF, effectiveAddr, finalValue);
            case "IndirectY" -> String.format("($%02X),Y = %04X @ %04X = %02X",
                    getFirstOperand(), baseAddr, effectiveAddr, finalValue);
            case "Relative" -> String.format("$%04X", PC + instructionSize + (byte) getFirstOperand());
            default -> "";
        };

        // Compute a raw operand string (without extra padding)
        String rawOperand = switch (instructionSize) {
            case 1 -> "";
            case 2 -> String.format("%02X", getFirstOperand()).trim();
            case 3 -> String.format("%02X %02X", getFirstOperand(), getSecondOperand()).trim();
            default -> "";
        };

        // For unofficial opcodes, adjust operand description if needed
        if (isUnofficial) {
            if (instructionSize == 2) {
                if (mode.equals("ZeroPage")) {
                    operandDesc = String.format("$%02X = %02X", getFirstOperand(), memory.read(getFirstOperand()));
                }
            } else if (instructionSize == 3) {
                if (mode.equals("AbsoluteX")) {
                    operandDesc = String.format("$%04X,X @ %04X = %02X", baseAddr, effectiveAddr, finalValue);
                } else if (mode.equals("AbsoluteY")) {
                    operandDesc = String.format("$%04X,Y @ %04X = %02X", baseAddr, effectiveAddr, finalValue);
                } else {
                    operandDesc = String.format("$%04X = %02X", baseAddr, memory.read(baseAddr));
                }
            }
        }

        String operandField = isUnofficial
                ? String.format("%-6s", rawOperand)
                : String.format("%-7s", rawOperand);

        String mnemonic = isUnofficial ? "*" + instruction : instruction;

        // Added CYC logging
        String fmt = isUnofficial
                ? "%04X  %02X %-6s%s %-26s  A:%02X X:%02X Y:%02X P:%02X SP:%02X CYC:%d\n"
                : "%04X  %02X %-7s%s %-26s  A:%02X X:%02X Y:%02X P:%02X SP:%02X CYC:%d\n";
        System.out.printf(fmt, PC, opcode, operandField, mnemonic, operandDesc.trim(), getA(), getX(), getY(),
                flags, getSP(), totalCycles);
    }

    // === Interrupt Handling ===
    public void nmi() {
        pushStack((PC >> 8) & 0xFF);
        pushStack(PC & 0xFF);
        pushStack(flags & 0xFF); // Push P with B flag off (usually?)
        // NMI disables I flag? No, NMI is Non-Maskable. IRQ disables I.
        // But usually standard Interrupt sequence applies.
        setFlag(FLAG_I, true);

        PC = memory.read(0xFFFA) | (memory.read(0xFFFB) << 8); // NMI Vector
        totalCycles += 7; // Takes 7 cycles
    }

    // === Main Execution
    // ==============================================================================================
    public void executeNextInstruction() {
        // Record cycle count before execution

        // Read opcode at current PC addr
        int opcode = memory.read(PC) & 0xFF;

        // Retrieve instruction and addressing mode using new arrays and Addresser
        String instruction = OP_NAMES[opcode];
        String mode = Addresser.getAddressingMode(opcode);

        // Reset page crossed flag
        pageBoundaryCrossed = false;

        // Determine instruction size based on addressing mode (used later to advance PC
        // based on size)
        int instructionSize = switch (mode) {
            case "Implied", "Accumulator" -> 1;
            case "Immediate", "ZeroPage", "ZeroPageX", "ZeroPageY", "Relative", "IndirectX", "IndirectY" -> 2;
            case "Absolute", "AbsoluteX", "AbsoluteY", "Indirect" -> 3;
            default -> 1;
        };

        // Jumping and Branching inst handle PC movement independently
        boolean pcUpdated = instruction.equals("JMP") || instruction.equals("JSR") || instruction.equals("RTS") ||
                instruction.startsWith("B");

        printInstructionLog(opcode, instruction, mode, instructionSize);

        // Add base cycles AFTER logging (so log shows start of instruction)
        int cycles = OP_CYCLES[opcode];
        totalCycles += cycles;

        switch (instruction) {
            // --- Control Instructions ---
            case "BRK": { // Software interrupt
                PC++; // Skip BRK opcode
                pushStack((PC >> 8) & 0xFF);
                pushStack(PC & 0xFF);
                pushStack(flags | 0x10); // Push status with Break flag set
                setFlag(FLAG_I, true); // Disable interrupts
                PC = memory.read(0xFFFE) | (memory.read(0xFFFF) << 8); // Jump to IRQ vector
                return;
            }
            case "NOP": {
                switch (opcode) {
                    case 0xEA: // Official NOP (1-byte)
                        break;
                    case 0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA: // 1-byte unofficial NOPs
                        break;
                    case 0x04, 0x44, 0x64, 0x80: // 2-byte unofficial NOPs (DOP variants)
                        memory.read(getFirstOperand());
                        break;
                    case 0x0C, 0x14, 0x1C, 0x34, 0x54, 0x74, 0xD4, 0xF4, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC: // 3-byte
                                                                                                       // unofficial
                                                                                                       // NOPs (TOP
                                                                                                       // variants)
                        // This usually does a read, which might trigger page crossing?
                        // "NOP" variants usually don't have page crossing penalty documented, but
                        // `nestest` might differ.
                        // My extracted table accounts for min cycles.
                        int addr = getEffectiveAddress(mode);
                        memory.read(addr);
                        if (pageBoundaryCrossed)
                            totalCycles++; // TOP (SKW) often has penalty
                        break;
                    default:
                        // System.out.printf("Unknown NOP Variant: 0x%02X\n", opcode);
                        break;
                }
                break;
            }
            case "PHP": { // Push Processor Status
                int status = flags | 0x30; // Ensure bits 4 (Break) and 5 (Unused) are set
                memory.write(0x0100 + getSP(), status);
                setSP(getSP() - 1);
                break;
            }
            case "PLP": {
                setFlagsFromByte(popStack()); // Restore flags from stack
                break;
            }
            case "RTI": { // Return from Interrupt
                setFlagsFromByte(popStack());
                PC = popStack();
                PC |= popStack() << 8;
                return;
            }
            // --- Branch Instructions ---
            // Note: branch() function handles cycle penalties
            case "BEQ":
                branch(getFlag(FLAG_Z));
                return;
            case "BNE":
                branch(!getFlag(FLAG_Z));
                return;
            case "BCS":
                branch(getFlag(FLAG_C));
                return;
            case "BCC":
                branch(!getFlag(FLAG_C));
                return;
            case "BMI":
                branch(getFlag(FLAG_N));
                return;
            case "BPL":
                branch(!getFlag(FLAG_N));
                return;
            case "BVS":
                branch(getFlag(FLAG_V));
                return;
            case "BVC":
                branch(!getFlag(FLAG_V));
                return;
            // --- Jump and Subroutine ---
            case "JMP": {
                if (mode.equals("Absolute"))
                    PC = ((memory.read(PC + 1) | (memory.read(PC + 2) << 8)) & 0xFFFF);
                else if (mode.equals("Indirect")) {
                    int indirectAddr = getFirstOperand() | (getSecondOperand() << 8);
                    int lowByte = memory.read(indirectAddr);
                    int highByte = memory.read((indirectAddr & 0xFF00) | ((indirectAddr + 1) & 0xFF));
                    PC = (highByte << 8) | lowByte;
                }
                return;
            }
            case "JSR": {
                int addr = getFirstOperand() | (getSecondOperand() << 8);
                int returnAddr = PC + 2;
                pushStack((returnAddr >> 8) & 0xFF);
                pushStack(returnAddr & 0xFF);
                PC = addr;
                return;
            }
            case "RTS": {
                PC = popStack(); // Pull PC low byte
                PC |= popStack() << 8; // Pull PC high byte
                PC++; // Move to next instruction after subroutine
                break;
            }
            // --- Data Transfer / Load & Store ---
            case "ORA": {
                int value = fetchOperandValue(mode);
                setA(getA() | value);
                setZeroAndNegativeFlags(getA());
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "LDA": {
                int value = fetchOperandValue(mode);
                setA(value);
                setZeroAndNegativeFlags(value);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "LAX": {
                int value = fetchOperandValue(mode);
                setA(value);
                setX(value);
                setZeroAndNegativeFlags(value);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "LDX": {
                int value = fetchOperandValue(mode);
                setX(value);
                setZeroAndNegativeFlags(value);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "LDY": {
                int value = fetchOperandValue(mode);
                setY(value);
                setZeroAndNegativeFlags(value);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "STA": {
                int addr = getEffectiveAddress(mode);
                memory.write(addr, getA());
                // Stores do not incur page crossing penalty
                break;
            }
            case "STX": {
                int addr = getEffectiveAddress(mode);
                memory.write(addr, getX());
                break;
            }
            case "STY": {
                int addr = getEffectiveAddress(mode);
                memory.write(addr, getY());
                break;
            }
            case "SAX": {
                int addr = getEffectiveAddress(mode);
                int storeValue = getA() & getX();
                memory.write(addr, storeValue);
                break;
            }
            // --- ALU Operations ---
            case "ADC": {
                int value = fetchOperandValue(mode);
                int carry = getFlag(FLAG_C) ? 1 : 0;
                int result = getA() + value + carry;
                setFlag(FLAG_C, result > 0xFF);
                setFlag(FLAG_V, ((getA() ^ result) & (value ^ result) & 0x80) != 0);
                setA(result & 0xFF);
                setZeroAndNegativeFlags(getA());
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "SBC", "ISB": {
                int value;
                if (instruction.equals("ISB")) { // ISB = INC + SBC (RMW)
                    int addr = getEffectiveAddress(mode);
                    int memVal = memory.read(addr);
                    memVal = (memVal + 1) & 0xFF;
                    memory.write(addr, memVal);
                    value = memVal;
                    // ISB is RMW, so no page crossing penalty on top of the RMW cycles usually?
                    // ISB cycles in table (e.g. 8) should cover it.
                } else {
                    value = fetchOperandValue(mode);
                    if (pageBoundaryCrossed)
                        totalCycles++;
                }
                value ^= 0xFF;
                int carry = getFlag(FLAG_C) ? 1 : 0;
                int result = getA() + value + carry;
                setFlag(FLAG_C, result > 0xFF);
                setFlag(FLAG_V, ((getA() ^ result) & (value ^ result) & 0x80) != 0);
                setA(result & 0xFF);
                setZeroAndNegativeFlags(getA());
                break;
            }
            case "AND": {
                int value = fetchOperandValue(mode);
                int newA = getA() & value;
                setA(newA);
                setZeroAndNegativeFlags(newA);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "EOR": {
                int value = fetchOperandValue(mode);
                int newA = getA() ^ value;
                setA(newA);
                setZeroAndNegativeFlags(newA);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "INY": // Increment Y Register
                setY((getY() + 1) & 0xFF); // Wrap around at 255
                setZeroAndNegativeFlags(getY());
                break;
            case "INX": // Increment X Register
                setX((getX() + 1) & 0xFF); // Wrap around at 255
                setZeroAndNegativeFlags(getX());
                break;
            case "DEY": // Decrement Y Register
                setY((getY() - 1) & 0xFF); // Decrement and wrap around 8-bit
                setZeroAndNegativeFlags(getY()); // Update flags
                break;
            case "DEX": // Decrement X Register
                setX((getX() - 1) & 0xFF); // Decrement and wrap around 8-bit
                setZeroAndNegativeFlags(getX()); // Update flags
                break;
            // --- Shift / Rotate Operations ---
            case "LSR": {
                if (mode.equals("Accumulator")) {
                    int value = getA();
                    setFlag(FLAG_C, (value & 0x01) != 0);
                    value >>= 1;
                    setA(value);
                    setZeroAndNegativeFlags(value);
                    setFlag(FLAG_N, false);
                } else {
                    int addr = getEffectiveAddress(mode);
                    int value = memory.read(addr);
                    setFlag(FLAG_C, (value & 0x01) != 0);
                    value >>= 1;
                    memory.write(addr, value);
                    setZeroAndNegativeFlags(value);
                    setFlag(FLAG_N, false);
                }
                break;
            }
            case "ASL": {
                if (mode.equals("Accumulator")) {
                    int value = getA();
                    setFlag(FLAG_C, (value & 0x80) != 0);
                    int result = (value << 1) & 0xFF;
                    setA(result);
                    setZeroAndNegativeFlags(result);
                } else {
                    int addr = getEffectiveAddress(mode);
                    int value = memory.read(addr);
                    setFlag(FLAG_C, (value & 0x80) != 0);
                    int result = (value << 1) & 0xFF;
                    memory.write(addr, result);
                    setZeroAndNegativeFlags(result);
                }
                break;
            }
            case "ROR": {
                boolean oldCarry = getFlag(FLAG_C);
                if (mode.equals("Accumulator")) {
                    int value = getA();
                    int newCarry = value & 0x01;
                    value = (value >> 1) | (oldCarry ? 0x80 : 0x00);
                    setFlag(FLAG_C, newCarry != 0);
                    setA(value);
                    setZeroAndNegativeFlags(value);
                } else {
                    int addr = getEffectiveAddress(mode);
                    int value = memory.read(addr);
                    int newCarry = value & 0x01;
                    value = (value >> 1) | (oldCarry ? 0x80 : 0x00);
                    setFlag(FLAG_C, newCarry != 0);
                    memory.write(addr, value);
                    setZeroAndNegativeFlags(value);
                }
                break;
            }
            case "ROL": {
                boolean oldCarry = getFlag(FLAG_C);
                if (mode.equals("Accumulator")) {
                    int value = getA();
                    int newCarry = (value & 0x80) != 0 ? 1 : 0;
                    value = ((value << 1) & 0xFF) | (oldCarry ? 1 : 0);
                    setFlag(FLAG_C, newCarry == 1);
                    setA(value);
                    setZeroAndNegativeFlags(value);
                } else {
                    int addr = getEffectiveAddress(mode);
                    int value = memory.read(addr);
                    int newCarry = (value & 0x80) != 0 ? 1 : 0;
                    value = ((value << 1) & 0xFF) | (oldCarry ? 1 : 0);
                    setFlag(FLAG_C, newCarry == 1);
                    memory.write(addr, value);
                    setZeroAndNegativeFlags(value);
                }
                break;
            }
            case "SLO": {
                int addr = getEffectiveAddress(mode); // Compute effective address based on addressing mode
                int value = memory.read(addr); // Read value from memory
                setFlag(FLAG_C, (value & 0x80) != 0); // Set Carry flag based on bit 7 of original value
                value = (value << 1) & 0xFF; // Shift left and ensure 8-bit result
                memory.write(addr, value); // Write shifted value back to memory
                int result = getA() | value;
                setA(result);
                setZeroAndNegativeFlags(result); // Update Zero and Negative flags
                break;
            }
            case "RLA": {
                int addr = getEffectiveAddress(mode); // Compute effective address based on addressing mode
                int value = memory.read(addr); // Read the value from memory
                boolean oldCarry = getFlag(FLAG_C); // Save the old Carry flag
                boolean newCarry = (value & 0x80) != 0; // Determine new Carry flag from bit 7
                value = ((value << 1) & 0xFF) | (oldCarry ? 1 : 0); // Rotate left including the old carry
                setFlag(FLAG_C, newCarry); // Update the Carry flag
                memory.write(addr, value); // Write the rotated value back to memory
                int result = getA() & value;
                setA(result);
                setZeroAndNegativeFlags(result); // Update Zero and Negative flags
                break;
            }
            case "SRE": {
                int addr = getEffectiveAddress(mode); // Compute effective address based on addressing mode
                int value = memory.read(addr); // Read value from memory
                setFlag(FLAG_C, (value & 0x01) != 0); // Set Carry flag from bit 0 of original value
                value = (value >> 1) & 0xFF; // Logical shift right, ensuring 8-bit result
                memory.write(addr, value); // Write shifted value back to memory
                int result = getA() ^ value;
                setA(result);
                setZeroAndNegativeFlags(result); // Update Zero and Negative flags
                break;
            }
            case "RRA": {
                int addr = getEffectiveAddress(mode); // Compute effective address using the addressing mode
                int value = memory.read(addr); // Read value from memory
                boolean oldCarry = getFlag(FLAG_C); // Save the old carry flag
                int newCarryBit = value & 0x01; // Bit 0 becomes the new carry
                value = (value >> 1) | (oldCarry ? 0x80 : 0); // Rotate right: shift, then add old carry into bit 7
                setFlag(FLAG_C, newCarryBit != 0); // Update carry flag
                memory.write(addr, value); // Write the rotated value back to memory

                int a = getA();
                int c = getFlag(FLAG_C) ? 1 : 0;
                int result = a + value + c;
                setFlag(FLAG_C, result > 0xFF);
                setFlag(FLAG_V, ((a ^ result) & (value ^ result) & 0x80) != 0);
                setA(result & 0xFF);
                setZeroAndNegativeFlags(getA());
                break;
            }
            // --- Compare and Test Operations ---
            case "CMP": {
                int value = fetchOperandValue(mode);
                int result = getA() - value;
                setFlag(FLAG_C, getA() >= value);
                setZeroAndNegativeFlags(result);
                if (pageBoundaryCrossed)
                    totalCycles++;
                break;
            }
            case "CPY": {
                int value = fetchOperandValue(mode);
                int result = getY() - value;
                setFlag(FLAG_C, getY() >= value);
                setZeroAndNegativeFlags(result);
                // CPY/CPX can have page crossing? Usually yes if AbsX/AbsY/IndY.
                // But CPY supports Immed, ZP, Abs. No indexed.
                // CPX supports Immed, ZP, Abs. No indexed.
                // Wait, check matrix.
                // C0: CPY Imm
                // C4: CPY ZP
                // CC: CPY Abs
                // No CPY ZP,X or Abs,X.
                // So no crossing possible.
                break;
            }
            case "CPX": {
                int value = fetchOperandValue(mode);
                int result = getX() - value;
                setFlag(FLAG_C, getX() >= value);
                setZeroAndNegativeFlags(result);
                break;
            }
            case "BIT": {
                int addr = getEffectiveAddress(mode);
                int value = memory.read(addr);
                setFlag(FLAG_Z, (getA() & value) == 0);
                setFlag(FLAG_N, (value & 0x80) != 0);
                setFlag(FLAG_V, (value & 0x40) != 0);
                PC += mode.equals("ZeroPage") ? 2 : 3;
                return;
            }
            // --- Register Transfers ---
            case "TAY": // Transfer A to Y
                setY(getA()); // Copy A register to Y
                setZeroAndNegativeFlags(getY()); // Update flags
                break;
            case "TAX": // Transfer A to X
                setX(getA()); // Copy A register to X
                setZeroAndNegativeFlags(getX()); // Update flags
                break;
            case "TYA": // Transfer Y to A
                setA(getY()); // Copy Y register to A
                setZeroAndNegativeFlags(getA()); // Update flags
                break;
            case "TXA": // Transfer X to A
                setA(getX()); // Copy X register to A
                setZeroAndNegativeFlags(getA()); // Update flags
                break;
            case "TSX": // Transfer Stack Pointer to X
                setX(getSP()); // Copy Stack Pointer (SP) to X register
                setZeroAndNegativeFlags(getX()); // Update flags
                break;
            case "TXS": // Transfer X to Stack Pointer
                setSP(getX()); // Set Stack Pointer to X register
                break;
            case "TSY": // Transfer Stack Pointer to Y (Unofficial Opcode)
                setY(getSP()); // Copy Stack Pointer (SP) to Y register
                setZeroAndNegativeFlags(getY()); // Update flags
                break;
            // --- Flag Operations ---
            case "CLC":
                setFlag(FLAG_C, false);
                break;
            case "SEC":
                setFlag(FLAG_C, true);
                break;
            case "CLD":
                setFlag(FLAG_D, false);
                break;
            case "SED":
                setFlag(FLAG_D, true);
                break;
            case "CLI":
                setFlag(FLAG_I, false);
                break;
            case "SEI":
                setFlag(FLAG_I, true);
                break;
            case "CLV":
                setFlag(FLAG_V, false);
                break;
            // --- Memory Operations ---
            case "PHA":
                pushStack(getA());
                break;
            case "PLA": {
                setSP(getSP() + 1);
                setA(memory.read(0x0100 + getSP()));
                setZeroAndNegativeFlags(getA());
                break;
            }
            case "INC": {
                int addr = getEffectiveAddress(mode);
                int value = (memory.read(addr) + 1) & 0xFF;
                memory.write(addr, value);
                setZeroAndNegativeFlags(value);
                break;
            }
            case "DEC": {
                int addr = getEffectiveAddress(mode);
                int value = (memory.read(addr) - 1) & 0xFF;
                memory.write(addr, value);
                setZeroAndNegativeFlags(value);
                break;
            }
            case "DCP": { // Illegal opcode: Decrement Memory, then Compare
                int addr = getEffectiveAddress(mode);
                int value = (memory.read(addr) - 1) & 0xFF; // Decrement and wrap around 8-bit
                memory.write(addr, value);
                int cmpResult = getA() - value;
                setFlag(FLAG_C, getA() >= value);
                setZeroAndNegativeFlags(cmpResult);
                break;
            }
            default:
                System.out.printf("Unknown Opcode: 0x%02X\n", opcode);
                break;
        }

        if (!pcUpdated) {
            PC += instructionSize; // Advance Program Counter based on inst size
        }
    }
}