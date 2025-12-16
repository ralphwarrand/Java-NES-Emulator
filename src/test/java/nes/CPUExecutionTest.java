package nes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import nes.CPU;
import nes.Memory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class CPUExecutionTest {

    private static String cleanLogLine(String logLine) {
        int ppuIndex = logLine.indexOf("PPU:");
        int cycIndex = logLine.indexOf("CYC:");

        if (ppuIndex != -1 && cycIndex != -1) {
            String part1 = logLine.substring(0, ppuIndex).trim();
            String part2 = logLine.substring(cycIndex).trim();
            return part1 + " " + part2;
        }
        return logLine.trim();
    }

    private List<String> loadReferenceLog(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    @Test
    public void testIncAbsX_BusActivity() {
        // Setup: INC $10FF, X (Where X=1) -> Target $1100.
        // Opcode: FE FF 10
        // Memory[$1100] = 0x55.
        // Expected:
        // T1: Fetch FE (PC=$0000)
        // T2: Fetch FF (PC=$0001)
        // T3: Fetch 10 (PC=$0002)
        // T4: Read Invalid: $1000 (Low+X | High<<8) = (FF+1)|1000 = 00|1000 = $1000.
        // T5: Read Valid: $1100 (Value 0x55)
        // T6: Write Old: $1100 (Value 0x55)
        // T7: Write New: $1100 (Value 0x56)

        Memory memory = new Memory(); // Assuming a default constructor for Memory
        CPU cpu = new CPU(memory);
        cpu.reset();
        cpu.setReg(CPU.Register.X, (byte) 1); // X = 1

        memory.ram[0] = (byte) 0xFE;
        memory.ram[1] = (byte) 0xFF;
        memory.ram[2] = (byte) 0x10;

        memory.ram[0x1000] = (byte) 0xAA; // Invalid addr content
        memory.ram[0x1100] = (byte) 0x55; // Valid addr content

        // Execute one instruction
        cpu.executeNextInstruction();

        // Assertions are hard without cycle-stepping.
        // But we can check final state and total cycles.
        assertEquals(7, cpu.getTotalCycles());
        assertEquals(0x56, memory.read(0x1100));
        assertEquals(0x56, memory.openBus); // Last value on bus
    }

    @Test
    public void testCPUExecutionMatchesReferenceLog() {
        try {
            // Setup memory and CPU
            Memory memory = new Memory("resources/nestest.nes");
            CPU cpu = new CPU(memory);
            cpu.reset(0xC000); // Set CPU to the correct starting point
            cpu.setLoggingEnabled(true); // Enable logging for output capture

            // Load the reference log
            List<String> referenceLog = loadReferenceLog("resources/nestest.log.txt");

            // Loop through the reference log and execute CPU instructions one at a time.
            for (int i = 0; i < referenceLog.size(); i++) {
                // Capture the CPU log output by redirecting System.out
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream testOut = new PrintStream(outputStream);
                PrintStream originalOut = System.out;
                System.setOut(testOut);

                cpu.executeNextInstruction(); // Execute one CPU instruction

                // Restore original System.out
                System.setOut(originalOut);

                String cpuOutput = outputStream.toString().trim();
                String expected = cleanLogLine(referenceLog.get(i));
                String actual = cleanLogLine(cpuOutput);

                // Assert that the expected and actual log lines match
                assertEquals("Mismatch at step " + i, expected, actual);
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException occurred: " + e.getMessage());
        }
    }
}