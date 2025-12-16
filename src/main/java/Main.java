import nes.NES;
import nes.CPU;
import nes.EmulatorRunner;
import nes.Memory;
import nes.gui.DebuggerWindow;
import nes.gui.Display;
import javax.swing.SwingUtilities;

import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--verify")) {
            runVerification();
        } else {
            String romPath = args.length > 0 ? args[0] : "resources/nestest.nes";
            runGameLoop(romPath);
        }
    }

    private static void runVerification() {
        try {
            System.out.println("Beginning Verification against nestest.log...");
            Memory testMemory = new Memory("resources/nestest.nes");
            CPU testCpu = new CPU(testMemory);
            testMemory.setCPU(testCpu);
            testCpu.reset(0xC000); // Reset CPU to Start of Test (Automated)
            testCpu.setLoggingEnabled(true);

            List<String> referenceLog = loadReferenceLog("resources/nestest.log.txt");

            // Execute instructions and verify
            for (int i = 0; i < referenceLog.size(); i++) {
                // Capture output
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                PrintStream originalOut = System.out;

                System.setOut(printStream);
                testCpu.executeNextInstruction();
                System.setOut(originalOut);

                String cpuOutput = outputStream.toString().trim();
                String expected = cleanLogLine(referenceLog.get(i));
                String actual = cleanLogLine(cpuOutput);

                if (!expected.equals(actual)) {
                    System.out.printf("\n❌ Mismatch at step %d\nEXPECTED: %s\nACTUAL  : %s\n", i, expected, actual);
                    System.exit(1); // Fail
                }
            }
            System.out.println("✅ Verification Successful!");
            System.exit(0); // Pass

        } catch (IOException e) {
            System.err.println("Verification Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runGameLoop(String romPath) {
        try {
            // GUI Initialization (EDT recommended, but simple here)
            Display display = new Display();

            // Core Initialization
            NES nes = new NES(display);
            nes.loadROM(romPath);

            // Connect Controller
            // Display already has key listener, needs to feed NES controller
            display.setController(nes.getController()); // Controller created inside NES now

            // Start Emulation Thread
            System.out.println("Starting Emulation Thread...");
            EmulatorRunner runner = new EmulatorRunner(nes);

            // Initial Reset
            nes.reset();

            // Start
            runner.start();

            System.out.println("Emulator running.");

            // Launch Debugger
            SwingUtilities.invokeLater(() -> {
                DebuggerWindow debugger = new DebuggerWindow(nes, runner);
                debugger.setVisible(true);
            });

        } catch (IOException e) {
            System.err.println("Error loading ROM: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
