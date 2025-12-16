package nes;

import nes.gui.Display;
import java.io.IOException;

public class NES {
    private CPU cpu;
    private PPU ppu;
    private APU apu;
    private Memory memory;
    private Controller controller;

    public NES(Display display) {
        // Initialize Components
        controller = new Controller();
        apu = new APU();
        ppu = new PPU(display);
    }

    public void loadROM(String romPath) throws IOException {
        System.out.println("Loading ROM: " + romPath);
        memory = new Memory(romPath);

        // Wiring
        ppu.setMemory(memory);
        memory.setPPU(ppu);
        memory.setAPU(apu);
        memory.setController1(controller);
        apu.setMemory(memory);

        cpu = new CPU(memory);
        memory.setCPU(cpu);
    }

    public void setController(Controller controller) {
        this.controller = controller;
        if (memory != null)
            memory.setController1(controller);
    }

    public void reset() {
        if (cpu != null) {
            cpu.reset();
        }
    }

    // Getters for Debugger
    public CPU getCpu() {
        return cpu;
    }

    public PPU getPpu() {
        return ppu;
    }

    public APU getApu() {
        return apu;
    }

    public Memory getMemory() {
        return memory;
    }

    public Controller getController() {
        return controller;
    }
}
