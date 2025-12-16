package nes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

// Sync-to-Audio Implementation
public class APU {

    // Audio Output
    private final SourceDataLine line;
    private final byte[] outputBuffer = new byte[1470];
    private int outputIndex = 0;

    // NTSC CPU Frequency = 1.789773 MHz
    // Sample Rate = 44100 Hz
    // Cycles per Sample = 1789773 / 44100 = ~40.58
    private static final double CYCLES_PER_SAMPLE = 40.5844;
    private double cycleCounter = 0; // Tracks when to emit a sample

    // Accumulators for Oversampling
    private double p1Sum = 0;
    private double p2Sum = 0;
    private double triSum = 0;
    private double noiseSum = 0;
    private double dmcSum = 0;
    private int sampleCount = 0; // Number of CPU cycles accumulated

    // External Dependencies
    private Memory memory;
    private CPU cpu;

    // --- IRQ ---
    public boolean irqActive = false;
    private boolean frameIrqEnabled = false;
    private boolean frameIrqActive = false;
    private boolean dmcIrqActive = false;

    // --- Frame Counter ---
    private int frameCounter = 0;
    private boolean frameCounterMode = false; // 0: 4-step, 1: 5-step
    private int frameCycle = 0;
    private int frameCounterResetDelay = 0; // Delay cycles before reset affects state

    // --- Components ---

    // Channel Enabled Flags (controlled by $4015)
    private boolean p1Enabled = false;
    private boolean p2Enabled = false;
    private boolean triEnabled = false;
    private boolean noiseEnabled = false;

    // Pulse 1
    private final Envelope p1Envelope = new Envelope();
    private final Sweep p1Sweep = new Sweep(p1Envelope);
    private int p1Duty = 0;
    private boolean p1ConstantVol = false;
    private int p1Volume = 0;
    private int p1TimerLow = 0;
    private int p1TimerHigh = 0;
    private int p1Timer = 0; // Current Timer Value
    private int p1LengthCounter = 0;
    private int p1Sequence = 0;

    // Pulse 2
    private final Envelope p2Envelope = new Envelope();
    private final Sweep p2Sweep = new Sweep(p2Envelope);
    private int p2Duty = 0;
    private boolean p2ConstantVol = false;
    private int p2Volume = 0;
    private int p2TimerLow = 0;
    private int p2TimerHigh = 0;
    private int p2Timer = 0;
    private int p2LengthCounter = 0;
    private int p2Sequence = 0;

    // Triangle
    private boolean triControl = false;
    private int triLinearCounterReload = 0;
    private int triTimerLow = 0;
    private int triTimerHigh = 0;
    private int triTimer = 0;
    private int triLengthCounter = 0;
    private int triLinearCounter = 0;
    private boolean triReloadLinear = false;
    private int triSequence = 0;

    // Noise
    private final Envelope noiseEnvelope = new Envelope();
    private boolean noiseLoop = false;
    private boolean noiseConstantVol = false;
    private int noiseVolume = 0;
    private boolean noiseMode = false;
    private int noisePeriod = 0;
    private int noiseTimer = 0;
    private int noiseLengthCounter = 0;
    private int noiseShiftRegister = 1;

    // DMC
    private boolean dmcIrqEnabled = false;
    private boolean dmcLoop = false;
    private int dmcRateIndex = 0;
    private int dmcOutputLevel = 0;
    private int dmcSampleAddress = 0;
    private int dmcSampleLength = 0;
    private int dmcCurrentAddress = 0;
    private int dmcBytesRemaining = 0;
    private int dmcBuffer = 0;
    private boolean dmcBufferEmpty = true;
    private int dmcShiftRegister = 0;
    private int dmcBitsRemaining = 8;
    private boolean dmcSilence = true;
    private int dmcPeriod = 0;
    private int dmcTimer = 0;

    // Filter State
    private double prevSample = 0;
    private double prevOutput = 0;

    // Tables
    private static final int[][] DUTY_TABLE = {
            { 0, 1, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 1, 1, 1, 1, 0, 0, 0 },
            { 1, 0, 0, 1, 1, 1, 1, 1 }
    };

    private static final int[] LENGTH_TABLE = {
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };

    private static final int[] TRIANGLE_SEQUENCE = {
            15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    private static final int[] NOISE_PERIOD_TABLE = {
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };

    private static final int[] DMC_RATE_TABLE = {
            428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
    };

    public APU() {
        SourceDataLine tempLine = null;
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            tempLine = AudioSystem.getSourceDataLine(format);
            tempLine.open(format, 4096);
            tempLine.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        this.line = tempLine;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    // === Register IO ===

    public int readRegister(int addr, int openBus) {
        if (addr == 0x4015) {
            // Status
            int val = 0;
            if (p1LengthCounter > 0)
                val |= 0x01;
            if (p2LengthCounter > 0)
                val |= 0x02;
            if (triLengthCounter > 0)
                val |= 0x04;
            if (noiseLengthCounter > 0)
                val |= 0x08;
            if (dmcBytesRemaining > 0)
                val |= 0x10;
            if (frameIrqActive)
                val |= 0x40;
            if (dmcIrqActive)
                val |= 0x80;

            // Bit 5 is unused (Open Bus)
            val |= (openBus & 0x20);

            frameIrqActive = false;
            updateIrqOutput();
            return val;
        }
        return -1; // Unmapped
    }

    public void writeRegister(int addr, int value) {
        switch (addr) {
            // Pulse 1
            case 0x4000:
                p1Duty = (value >> 6) & 0x03;
                p1Envelope.loop = (value & 0x20) != 0;
                p1ConstantVol = (value & 0x10) != 0;
                p1Envelope.constantVolume = (value & 0x10) != 0;
                p1Volume = value & 0x0F;
                p1Envelope.volumePeriod = value & 0x0F;
                break;
            case 0x4001:
                p1Sweep.enabled = (value & 0x80) != 0;
                p1Sweep.period = (value >> 4) & 0x07;
                p1Sweep.negate = (value & 0x08) != 0;
                p1Sweep.shift = value & 0x07;
                p1Sweep.reload = true;
                break;
            case 0x4002:
                p1TimerLow = value;
                p1Sweep.updateTargetPeriod(p1TimerLow | (p1TimerHigh << 8));
                break;
            case 0x4003:
                p1TimerHigh = value & 0x07;
                if (p1Enabled)
                    p1LengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F]; // Check enabled
                p1Sequence = 0;
                p1Envelope.start = true;
                p1Sweep.updateTargetPeriod(p1TimerLow | (p1TimerHigh << 8));
                break;

            // Pulse 2
            case 0x4004:
                p2Duty = (value >> 6) & 0x03;
                p2Envelope.loop = (value & 0x20) != 0;
                p2ConstantVol = (value & 0x10) != 0;
                p2Envelope.constantVolume = (value & 0x10) != 0;
                p2Volume = value & 0x0F;
                p2Envelope.volumePeriod = value & 0x0F;
                break;
            case 0x4005:
                p2Sweep.enabled = (value & 0x80) != 0;
                p2Sweep.period = (value >> 4) & 0x07;
                p2Sweep.negate = (value & 0x08) != 0;
                p2Sweep.shift = value & 0x07;
                p2Sweep.reload = true;
                break;
            case 0x4006:
                p2TimerLow = value;
                p2Sweep.updateTargetPeriod(p2TimerLow | (p2TimerHigh << 8));
                break;
            case 0x4007:
                p2TimerHigh = value & 0x07;
                if (p2Enabled)
                    p2LengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F]; // Check enabled
                p2Sequence = 0;
                p2Envelope.start = true;
                p2Sweep.updateTargetPeriod(p2TimerLow | (p2TimerHigh << 8));
                break;

            // Triangle
            case 0x4008:
                triControl = (value & 0x80) != 0;
                triLinearCounterReload = value & 0x7F;
                break;
            case 0x4009:
                break;
            case 0x400A:
                triTimerLow = value;
                break;
            case 0x400B:
                triTimerHigh = value & 0x07;
                if (triEnabled)
                    triLengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F]; // Check enabled
                triReloadLinear = true;
                break;

            // Noise
            case 0x400C:
                noiseLoop = (value & 0x20) != 0;
                noiseEnvelope.loop = (value & 0x20) != 0;
                noiseConstantVol = (value & 0x10) != 0;
                noiseEnvelope.constantVolume = (value & 0x10) != 0;
                noiseVolume = value & 0x0F;
                noiseEnvelope.volumePeriod = value & 0x0F;
                break;
            case 0x400E:
                noiseMode = (value & 0x80) != 0;
                noisePeriod = NOISE_PERIOD_TABLE[value & 0x0F];
                break;
            case 0x400F:
                if (noiseEnabled)
                    noiseLengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F]; // Check enabled
                noiseEnvelope.start = true;
                break;

            // DMC
            case 0x4010:
                boolean irqEnabled = (value & 0x80) != 0;
                dmcLoop = (value & 0x40) != 0;
                dmcRateIndex = value & 0x0F;
                dmcPeriod = DMC_RATE_TABLE[dmcRateIndex];
                if (!irqEnabled) {
                    dmcIrqActive = false;
                    updateIrqOutput();
                }
                dmcIrqEnabled = irqEnabled;
                break;
            case 0x4011:
                dmcOutputLevel = value & 0x7F;
                break;
            case 0x4012:
                dmcSampleAddress = 0xC000 + (value * 64);
                break;
            case 0x4013:
                dmcSampleLength = (value * 16) + 1;
                break;

            // Control & Status
            case 0x4015:
                p1Enabled = (value & 0x01) != 0;
                p2Enabled = (value & 0x02) != 0;
                triEnabled = (value & 0x04) != 0;
                noiseEnabled = (value & 0x08) != 0;

                if (!p1Enabled)
                    p1LengthCounter = 0;
                if (!p2Enabled)
                    p2LengthCounter = 0;
                if (!triEnabled)
                    triLengthCounter = 0;
                if (!noiseEnabled)
                    noiseLengthCounter = 0;

                if ((value & 0x10) != 0) {
                    if (dmcBytesRemaining == 0) {
                        dmcCurrentAddress = dmcSampleAddress;
                        dmcBytesRemaining = dmcSampleLength;
                    }
                } else {
                    dmcBytesRemaining = 0;
                    dmcIrqActive = false; // Disable clears IRQ
                    updateIrqOutput();
                }
                break;

            case 0x4017:
                pendingFrameCounterMode = (value & 0x80) != 0;
                pendingFrameIrqEnabled = (value & 0x40) == 0;

                if (!pendingFrameIrqEnabled) {
                    // Disable Frame IRQ immediately
                    frameIrqActive = false;
                    frameIrqEnabled = false; // Also update current enabled state immediate?
                    // No, 'pendingFrameIrqEnabled' applies to the *Sequence*.
                    // But the *Flag* clearing is immediate.
                    updateIrqOutput();
                }
                pendingWrite = true;

                // Jitter/Delay Logic
                // With CPU.totalCycles updated at START of instruction,
                // we are now writing at the "End Time" (Cycle 3/4).
                // This matches hardware behavior.
                // HW: Odd Write -> Delay 4, Even Write -> Delay 3.
                if (cpu != null && (cpu.getTotalCycles() & 1) != 0) {
                    frameCounterResetDelay = 4;
                } else {
                    frameCounterResetDelay = 3;
                }
                break;
        }
    }

    private void updateIrqOutput() {
        irqActive = frameIrqActive || dmcIrqActive;
    }

    // === Execution ===

    // Pending State for $4017
    private boolean pendingFrameCounterMode;
    private boolean pendingFrameIrqEnabled;
    private boolean pendingWrite;

    public void tick() {
        // Run logic EVERY cycle (Oversampling)

        // 1. Step Channels
        stepPulse1();
        stepPulse2();
        stepTriangle();
        stepNoise();
        stepDMC();

        // 2. Accumulate Outputs
        if (p1LengthCounter > 0 && !p1Sweep.mute && DUTY_TABLE[p1Duty][p1Sequence] != 0) {
            p1Sum += p1Envelope.output;
        }
        if (p2LengthCounter > 0 && !p2Sweep.mute && DUTY_TABLE[p2Duty][p2Sequence] != 0) {
            p2Sum += p2Envelope.output;
        }
        if (triLengthCounter > 0 && triLinearCounter > 0) {
            triSum += TRIANGLE_SEQUENCE[triSequence];
        }
        if (noiseLengthCounter > 0 && (noiseShiftRegister & 0x01) == 0) {
            noiseSum += noiseEnvelope.output;
        }
        if (dmcBytesRemaining > 0 || dmcBitsRemaining > 0) {
            dmcSum += dmcOutputLevel;
        }

        sampleCount++;
        cycleCounter++;

        // 3. Downsample
        if (cycleCounter >= CYCLES_PER_SAMPLE) {
            cycleCounter -= CYCLES_PER_SAMPLE;
            generateSample();

            p1Sum = 0;
            p2Sum = 0;
            triSum = 0;
            noiseSum = 0;
            dmcSum = 0;
            sampleCount = 0;
        }

        // 4. Frame Counter Stepping (Handle Reset Delay)
        if (frameCounterResetDelay > 0) {
            frameCounterResetDelay--;
            if (frameCounterResetDelay == 0) {
                // Apply Pending State
                if (pendingWrite) {
                    frameCounterMode = pendingFrameCounterMode;
                    frameIrqEnabled = pendingFrameIrqEnabled;
                    if (!frameIrqEnabled) {
                        frameIrqActive = false;
                        updateIrqOutput();
                    }
                    pendingWrite = false;
                }

                frameCycle = 0;
                if (frameCounterMode) { // Mode 1: Clock immediately
                    clockQuarterFrame();
                    clockHalfFrame();
                }
            }
        }
        stepFrameCounter();
    }

    // --- Steppers ---
    private void stepPulse1() {
        if (p1Timer > 0) {
            p1Timer--;
        } else {
            p1Timer = (p1TimerLow | (p1TimerHigh << 8)) * 2 + 1; // Pulse runs at CPU/2
            p1Sequence = (p1Sequence + 1) & 7;
        }
    }

    private void stepPulse2() {
        if (p2Timer > 0) {
            p2Timer--;
        } else {
            p2Timer = (p2TimerLow | (p2TimerHigh << 8)) * 2 + 1;
            p2Sequence = (p2Sequence + 1) & 7;
        }
    }

    private void stepTriangle() {
        if (triTimer > 0) {
            triTimer--;
        } else {
            triTimer = triTimerLow | (triTimerHigh << 8);
            if (triLengthCounter > 0 && triLinearCounter > 0) {
                triSequence = (triSequence + 1) & 31;
            }
        }
    }

    private void stepNoise() {
        if (noiseTimer > 0) {
            noiseTimer--;
        } else {
            noiseTimer = noisePeriod;
            int feedback;
            if (noiseMode)
                feedback = (noiseShiftRegister & 0x01) ^ ((noiseShiftRegister >> 6) & 0x01);
            else
                feedback = (noiseShiftRegister & 0x01) ^ ((noiseShiftRegister >> 1) & 0x01);
            noiseShiftRegister >>= 1;
            noiseShiftRegister |= (feedback << 14);
        }
    }

    private void stepDMC() {
        if (dmcPeriod > 0) {
            if (dmcTimer > 0)
                dmcTimer--;
            else {
                dmcTimer = dmcPeriod;
                // DMC Logic
                if (!dmcSilence) {
                    if ((dmcShiftRegister & 0x01) != 0) {
                        if (dmcOutputLevel <= 125)
                            dmcOutputLevel += 2;
                    } else {
                        if (dmcOutputLevel >= 2)
                            dmcOutputLevel -= 2;
                    }
                    dmcShiftRegister >>= 1;
                    dmcBitsRemaining--;
                }
                if (dmcBitsRemaining <= 0) {
                    dmcBitsRemaining = 8;
                    if (dmcBufferEmpty) {
                        dmcSilence = true;
                    } else {
                        dmcSilence = false;
                        dmcShiftRegister = dmcBuffer;
                        dmcBufferEmpty = true;
                    }
                }
                if (dmcBufferEmpty && dmcBytesRemaining > 0) {
                    if (memory != null)
                        dmcBuffer = memory.read(dmcCurrentAddress);
                    dmcCurrentAddress = (dmcCurrentAddress + 1) & 0xFFFF;
                    if (dmcCurrentAddress == 0)
                        dmcCurrentAddress = 0x8000;
                    dmcBytesRemaining--;
                    if (dmcBytesRemaining == 0) {
                        if (dmcLoop) {
                            dmcCurrentAddress = dmcSampleAddress;
                            dmcBytesRemaining = dmcSampleLength;
                        } else if (dmcIrqEnabled) {
                            dmcIrqActive = true;
                            updateIrqOutput();
                        }
                    }
                    dmcBufferEmpty = false;
                }
            }
        }
    }

    // Kept +2 cycle delay logic from previous phase -> REVERTED
    private void stepFrameCounter() {
        frameCycle++;
        if (frameCycle == 7457)
            clockQuarterFrame();
        if (frameCycle == 14913) {
            clockQuarterFrame();
            clockHalfFrame();
        }
        if (frameCycle == 22371)
            clockQuarterFrame();
        if (frameCycle == 29829) {
            if (!frameCounterMode) {
                clockQuarterFrame();
                clockHalfFrame();
                if (frameIrqEnabled) {
                    frameIrqActive = true;
                    updateIrqOutput();
                }
            }
        }
        if (frameCycle == 37281) {
            if (frameCounterMode) {
                clockQuarterFrame();
                clockHalfFrame();
            }
            frameCycle = 0;
        }
        if (!frameCounterMode && frameCycle >= 29830)
            frameCycle = 0;
    }

    private void clockQuarterFrame() {
        p1Envelope.clock();
        p2Envelope.clock();
        noiseEnvelope.clock();

        if (triReloadLinear)
            triLinearCounter = triLinearCounterReload;
        else if (triLinearCounter > 0)
            triLinearCounter--;
        if (!triControl)
            triReloadLinear = false;
    }

    private void clockHalfFrame() {
        if (!p1Envelope.loop && p1LengthCounter > 0)
            p1LengthCounter--;
        if (!p2Envelope.loop && p2LengthCounter > 0)
            p2LengthCounter--;
        if (!triControl && triLengthCounter > 0)
            triLengthCounter--;
        if (!noiseLoop && noiseLengthCounter > 0)
            noiseLengthCounter--;

        p1Sweep.clock(p1TimerLow | (p1TimerHigh << 8), 0);
        p2Sweep.clock(p2TimerLow | (p2TimerHigh << 8), 1);
    }

    private void generateSample() {
        if (sampleCount == 0)
            return;

        double outP1 = p1Sum / sampleCount;
        double outP2 = p2Sum / sampleCount;
        double outTri = triSum / sampleCount;
        double outNoise = noiseSum / sampleCount;
        double outDMC = dmcSum / sampleCount; // Should accumulate DMC too, technically

        // --- Hardware Accurate Mixing ---
        double pulseOut = 0;
        if (outP1 > 0 || outP2 > 0) {
            pulseOut = 95.88 / ((8128.0 / (outP1 + outP2)) + 100.0);
        }

        double tndOut = 0;
        if (outTri > 0 || outNoise > 0 || outDMC > 0) {
            double denom = (outTri / 8227.0) + (outNoise / 12241.0) + (outDMC / 22638.0);
            if (denom > 0)
                tndOut = 159.79 / ((1.0 / denom) + 100.0);
        }

        double output = pulseOut + tndOut;

        // --- High Pass Filter ---
        double temp = output;
        output = temp - prevSample + 0.996 * prevOutput;
        prevSample = temp;
        prevOutput = output;

        if (output > 1.0)
            output = 1.0;
        if (output < -1.0)
            output = -1.0;

        short finalSample = (short) (output * 32767.0);

        outputBuffer[outputIndex++] = (byte) (finalSample & 0xFF);
        outputBuffer[outputIndex++] = (byte) ((finalSample >> 8) & 0xFF);

        if (outputIndex >= outputBuffer.length) {
            if (line != null)
                line.write(outputBuffer, 0, outputBuffer.length);
            outputIndex = 0;
        }
    }

    // === Inner Classes ===

    private class Envelope {
        public boolean start = false;
        public boolean loop = false;
        public boolean constantVolume = false;
        public int volumePeriod = 0;
        public int output = 0;
        private int decayCount = 0;
        private int dividerCount = 0;

        public void clock() {
            if (!start) {
                if (dividerCount == 0) {
                    dividerCount = volumePeriod;
                    if (decayCount > 0)
                        decayCount--;
                    else if (loop)
                        decayCount = 15;
                } else
                    dividerCount--;
            } else {
                start = false;
                decayCount = 15;
                dividerCount = volumePeriod;
            }
            if (constantVolume)
                output = volumePeriod;
            else
                output = decayCount;
        }
    }

    private class Sweep {
        public boolean enabled = false;
        public boolean negate = false;
        public boolean reload = false;
        public int period = 0;
        public int shift = 0;
        public boolean mute = false;
        private int divider = 0;
        private Envelope envelope;

        public Sweep(Envelope e) {
            this.envelope = e;
        }

        public void updateTargetPeriod(int currentPeriod) {
            int change = currentPeriod >> shift;
            int target;
            if (negate)
                target = currentPeriod - change;
            else
                target = currentPeriod + change;
            mute = (currentPeriod < 8) || (target > 0x7FF);
        }

        public void clock(int currentPeriod, int channel) {
            if (divider == 0 && enabled && !mute && shift > 0) {
                int change = currentPeriod >> shift;
                int target = negate ? (currentPeriod - change - (channel == 0 ? 1 : 0)) : (currentPeriod + change);
                if (target >= 0 && target <= 0x7FF) {
                    if (channel == 0) {
                        p1TimerLow = target & 0xFF;
                        p1TimerHigh = (target >> 8) & 0x07;
                    } else {
                        p2TimerLow = target & 0xFF;
                        p2TimerHigh = (target >> 8) & 0x07;
                    }
                }
            }
            if (divider == 0 || reload) {
                divider = period;
                reload = false;
            } else
                divider--;
            updateTargetPeriod(currentPeriod);
        }
    }

}
