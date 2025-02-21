import nes.cpu.CPU;
import nes.Memory;
import nes.MemoryViewer;

import java.io.*;
import java.util.*;

public class Main {

    private static String cleanLogLine(String logLine) {
        // Remove everything after "PPU:" to match expected format
        int ppuIndex = logLine.indexOf("PPU:");
        if (ppuIndex != -1) {
            return logLine.substring(0, ppuIndex).trim();
        }
        return logLine.trim();
    }

    public static void main(String[] args) {
        try {
            Memory memory = new Memory("resources/nestest.nes");
            CPU cpu = new CPU(memory);
            cpu.reset(0xC000); // Reset CPU to start at the correct entry point for test file

            // Initialize Memory Viewer
            MemoryViewer memoryViewer = new MemoryViewer(memory);
            memoryViewer.displayMemory(); // Start GUI

            // Load reference log file
            List<String> referenceLog = loadReferenceLog("resources/nestest.log.txt");

            // Store CPU execution log for comparison
            List<String> cpuExecutionLog = new ArrayList<>();

            // Execute instructions in a loop
            for (int i = 0; i < referenceLog.size(); i++) {
                // Capture the CPU log output
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                PrintStream originalOut = System.out;

                // Redirect System.out to capture CPU output
                System.setOut(printStream);

                cpu.executeNextInstruction();  // Run a single CPU instruction

                // Restore original System.out
                System.setOut(originalOut);

                // Store the CPU log output as a string
                String cpuOutput = outputStream.toString().trim();
                cpuExecutionLog.add(cpuOutput);

                String expected = cleanLogLine(referenceLog.get(i));
                String actual = cleanLogLine(cpuOutput);

                // Check for mismatch and stop execution
                if (!expected.equals(actual)) {
                    System.out.printf("\n❌ Mismatch at step %d\n", i);
                    System.out.printf("EXPECTED: %s\n", expected);
                    System.out.printf("ACTUAL  : %s\n", actual);
                    System.out.println("\n⛔ Stopping execution due to mismatch.");
                    return; // STOP EXECUTION on mismatch
                }

                // Print step-by-step execution
                System.out.printf("\n✅ Step %d - MATCH\n%s", i, actual);

                // Refresh memory viewer after each instruction
                memoryViewer.updateMemory();

                // Introduce a small delay for readability in GUI
               Thread.sleep(0); // Adjust for testing
            }

            System.out.println("\n✅ CPU Execution Matches Reference Log!");

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static List<String> loadReferenceLog(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        return lines;
    }
}
