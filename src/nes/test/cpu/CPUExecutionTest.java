package nes.test.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import nes.emu.CPU;
import nes.emu.Memory;

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
        if (ppuIndex != -1) {
            return logLine.substring(0, ppuIndex).trim();
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
    public void testCPUExecutionMatchesReferenceLog() {
        try {
            // Setup memory and CPU
            Memory memory = new Memory("resources/nestest.nes");
            CPU cpu = new CPU(memory);
            cpu.reset(0xC000); // Set CPU to the correct starting point

            // Load the reference log
            List<String> referenceLog = loadReferenceLog("resources/nestest.log.txt");

            // Loop through the reference log and execute CPU instructions one at a time.
            for (int i = 0; i < referenceLog.size(); i++) {
                // Capture the CPU log output by redirecting System.out
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream testOut = new PrintStream(outputStream);
                PrintStream originalOut = System.out;
                System.setOut(testOut);

                cpu.executeNextInstruction();  // Execute one CPU instruction

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