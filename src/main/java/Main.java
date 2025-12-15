import nes.CPU;
import nes.Controller;
import nes.Memory;
import nes.PPU;
import nes.gui.Display;

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
            // Initialize Real Components
            System.out.println("Loading ROM: " + romPath);
            Memory memory = new Memory(romPath);
            Display display = new Display();
            PPU ppu = new PPU(display);
            ppu.setMemory(memory); // Enable CHR Banking
            Controller controller = new Controller();

            // Connect components
            memory.setPPU(ppu);
            memory.setController1(controller);
            display.setController(controller);

            CPU cpu = new CPU(memory);

            // Start from standard reset vector
            cpu.reset();
            cpu.setLoggingEnabled(false); // Disable logging for speed

            System.out.println("Emulator started. Target: 60 FPS.");

            // 60 FPS Logic
            long targetFrameTime = 1_000_000_000 / 60; // Nanoseconds

            while (true) {
                long frameStart = System.nanoTime();

                // Run emulation until one frame is generated
                while (!ppu.frameComplete) {
                    // Execute ONE CPU instruction
                    long startInfo = cpu.getTotalCycles();
                    cpu.executeNextInstruction();
                    long cpuCycles = cpu.getTotalCycles() - startInfo;

                    // Run PPU for 3x CPU cycles
                    for (int i = 0; i < cpuCycles * 3; i++) {
                        ppu.tick();
                    }

                    // Check NMI
                    if (ppu.nmiOccurred) {
                        ppu.nmiOccurred = false;
                        cpu.nmi();
                    }
                }

                // End of Frame
                ppu.frameComplete = false;

                // Time Synchronization
                long frameTime = System.nanoTime() - frameStart;
                long sleepTime = targetFrameTime - frameTime;

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Yield occasionally to ensure UI responsiveness if load is high
                Thread.onSpinWait();
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
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
