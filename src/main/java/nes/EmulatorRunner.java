package nes;

public class EmulatorRunner implements Runnable {
    private final NES nes;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Thread thread;

    public EmulatorRunner(NES nes) {
        this.nes = nes;
    }

    public void start() {
        if (running)
            return;
        running = true;
        paused = false;
        thread = new Thread(this, "EmulatorThread");
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            if (thread != null)
                thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void step() {
        if (!paused)
            return; // Only step when paused
        // Execute one instruction
        stepCpu();
    }

    @Override
    public void run() {
        while (running) {
            if (paused) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            // Frame Loop
            // Using same logic as Main.java
            CPU cpu = nes.getCpu();
            PPU ppu = nes.getPpu();
            APU apu = nes.getApu();

            if (cpu == null || ppu == null)
                continue;

            // Generate one frame
            while (!ppu.frameComplete && running && !paused) {
                stepSystem(cpu, ppu, apu);
            }

            ppu.frameComplete = false;
            // No sleep needed as APU audio writes block to sync speed
        }
    }

    private void stepCpu() {
        CPU cpu = nes.getCpu();
        PPU ppu = nes.getPpu();
        APU apu = nes.getApu();
        if (cpu != null) {
            stepSystem(cpu, ppu, apu);
        }
    }

    // Single system step (CPU instruction + PPU/APU clocking)
    private void stepSystem(CPU cpu, PPU ppu, APU apu) {
        long lastTotalCycles = cpu.getTotalCycles();

        // Execute Instruction (or DMA step)
        cpu.executeNextInstruction();

        // Check Interrupts (NMI/IRQ)
        if (!cpu.isDmaActive()) {
            // NMI Edge Detection logic from previous refactor
            cpu.setNMI(ppu.nmiOccurred);
        }
        if (apu.irqActive && !cpu.isDmaActive()) {
            cpu.irq();
        }

        // Calculate Delta Cycles (Includes Instruction + Interrupts)
        long currentTotalCycles = cpu.getTotalCycles();
        long cyclesToRun = currentTotalCycles - lastTotalCycles;

        // Clock PPU (3x) and APU (1x)
        for (int i = 0; i < cyclesToRun * 3; i++) {
            ppu.tick();
        }

        for (int i = 0; i < cyclesToRun; i++) {
            apu.tick();
        }
    }
}
