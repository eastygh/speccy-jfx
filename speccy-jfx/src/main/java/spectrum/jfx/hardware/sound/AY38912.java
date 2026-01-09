package spectrum.jfx.hardware.sound;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;

@Slf4j
public class AY38912 implements InPortListener, OutPortListener, Sound, Device {

    // Registers AY-3-8912 (0-15)
    private final int[] regs = new int[16];
    private int selectedRegister = 0;

    private final MachineSettings machineSettings;

    // Logarithmic volume table (16 levels)
    private static final int[] VOL_TABLE = {
            0, 2, 3, 4, 6, 8, 11, 16, 23, 32, 45, 64, 90, 127, 180, 255
    };

    private static final int SAMPLE_RATE = 44100;
    private final AYAudioEngine engine;
    private double globalVolume = 1.0;
    private boolean isMuted = false;

    public AY38912(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        this.engine = new AYAudioEngine(machineSettings);
    }

    @Override
    public void init() {
        reset();
        log.info("AY-3-8912 Initialized");
    }

    @Override
    public void reset() {
        synchronized (this) {
            Arrays.fill(regs, 0);
            selectedRegister = 0;
            regs[7] = 0x3F; // Mixer: all tones and noise off
            engine.resetStates();
            engine.updateRegisters(regs);
        }
        log.debug("AY-3-8912 Reset");
    }

    @Override
    public void open() {
        if (!engine.isAlive()) {
            engine.start();
            log.debug("AY Audio Engine started");
        }
        engine.setPaused(false);
    }

    @Override
    public void close() {
        engine.setPaused(true);
    }

    @Override
    public int inPort(int port) {
        if ((port & 0xC002) == 0xC000) {
            return regs[selectedRegister] & 0xFF;
        }
        return 0xFF;
    }

    @Override
    public void outPort(int port, int value) {
        int maskedPort = port & 0xC002;
        value &= 0xFF;

        if (maskedPort == 0xC000) {
            selectedRegister = value & 0x0F;
        } else if (maskedPort == 0x8000) {
            writeRegister(selectedRegister, value);
        }
    }

    private synchronized void writeRegister(int reg, int value) {
        regs[reg] = value;
        engine.updateRegisters(regs);
        // Writing to Register 13 resets the Envelope Generator cycle
        if (reg == 13) {
            engine.resetEnvelope();
        }
    }

    @Override
    public double getVolume() {
        return globalVolume;
    }

    @Override
    public void setVolume(double volume) {
        this.globalVolume = Math.max(0.0, Math.min(1.0, volume));
        engine.setMasterGain(isMuted ? 0 : globalVolume);
    }

    @Override
    public void mute(boolean state) {
        this.isMuted = state;
        engine.setMasterGain(isMuted ? 0 : globalVolume);
    }

    @Override
    public void play(int cycles) {
    }

    @Override
    public void ticks(long tStates, int delta) {
    }

    @Override
    public void endFrame() {
    }

    @Override
    public void pushBackTape(boolean state) {
    }

    // --- Audio engine (DSP) ---

    private static class AYAudioEngine extends Thread {
        private final int[] activeRegs = new int[16];
        private SourceDataLine line;

        @Setter
        private volatile boolean paused = true;
        @Setter
        private double masterGain = 1.0;

        private final double ayClockFreq;

        // Generators states
        private final double[] toneCounters = new double[3];
        private final boolean[] toneStates = new boolean[3];
        private double noiseCounter = 0;
        private int noiseSeed = 1;
        private boolean noiseState = false;

        // Envelope states
        private double envCounter = 0;
        private int envStep = 0;
        private boolean envHold = false;
        private boolean envReverse = false;

        public AYAudioEngine(MachineSettings machineSettings) {
            this.ayClockFreq = (double) machineSettings.getMachineType().clockFreq / 2;
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, 1024);
                line.start();
                setDaemon(true);
            } catch (LineUnavailableException e) {
                log.error("Failed to initialize audio line", e);
            }
        }

        public synchronized void updateRegisters(int[] newRegs) {
            System.arraycopy(newRegs, 0, activeRegs, 0, 16);
        }

        public void resetStates() {
            Arrays.fill(toneCounters, 0);
            noiseCounter = 0;
            resetEnvelope();
        }

        public void resetEnvelope() {
            envCounter = 0;
            envStep = 0;
            envHold = false;
            envReverse = false;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[512];
            while (!isInterrupted()) {
                if (paused) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = generateNextSample();
                }
                line.write(buffer, 0, buffer.length);
            }
            line.close();
        }

        private byte generateNextSample() {
            int finalMix = 0;

            // 1. Noise Generator
            int noisePeriod = activeRegs[6] & 0x1F;
            if (noisePeriod == 0) noisePeriod = 1;
            double noiseStep = (ayClockFreq / (16.0 * noisePeriod)) / SAMPLE_RATE;
            noiseCounter += noiseStep;
            if (noiseCounter >= 1.0) {
                noiseCounter -= 1.0;
                if (((noiseSeed + 1) & 2) != 0) noiseState = !noiseState;
                if ((noiseSeed & 1) != 0) noiseSeed ^= 0x24000;
                noiseSeed >>= 1;
            }

            // 2. Envelope Generator
            int envPeriod = (activeRegs[11] & 0xFF) | ((activeRegs[12] & 0xFF) << 8);
            if (envPeriod == 0) envPeriod = 1;
            // Envelope clock is MASTER_CLOCK / 256
            double envStepSize = (ayClockFreq / (256.0 * envPeriod)) / SAMPLE_RATE;

            if (!envHold) {
                envCounter += envStepSize;
                if (envCounter >= 1.0) {
                    envCounter -= 1.0;
                    envStep++;
                    if (envStep > 31) {
                        processEnvelopeFlags();
                    }
                }
            }

            // 3. Mixing Tone & Noise
            int mixer = activeRegs[7];
            int currentEnvVol = calculateEnvVolume();

            for (int i = 0; i < 3; i++) {
                int tonePeriod = (activeRegs[i * 2] | ((activeRegs[i * 2 + 1] & 0x0F) << 8));
                if (tonePeriod == 0) tonePeriod = 1;

                double toneStep = (ayClockFreq / (16.0 * tonePeriod)) / SAMPLE_RATE;
                toneCounters[i] += toneStep;
                if (toneCounters[i] >= 1.0) {
                    toneCounters[i] -= 1.0;
                    toneStates[i] = !toneStates[i];
                }

                boolean toneOut = toneStates[i] | ((mixer & (1 << i)) != 0);
                boolean noiseOut = noiseState | ((mixer & (1 << (i + 3))) != 0);

                if (toneOut & noiseOut) {
                    int volReg = activeRegs[8 + i];
                    boolean useEnv = (volReg & 0x10) != 0;
                    int volIdx = useEnv ? currentEnvVol : (volReg & 0x0F);
                    finalMix += VOL_TABLE[volIdx];
                }
            }

            double mixed = (finalMix / 3.0) * masterGain;
            return (byte) (mixed - 128);
        }

        private void processEnvelopeFlags() {
            int shape = activeRegs[13] & 0x0F;
            boolean continueBit = (shape & 0x08) != 0;
            boolean attackBit = (shape & 0x04) != 0;
            boolean alternateBit = (shape & 0x02) != 0;
            boolean holdBit = (shape & 0x01) != 0;

            envStep = 0; // Reset counter for the next cycle

            if (!continueBit) {
                // If Continue is 0, the envelope runs once and then stays at 0
                // (or stays at 31 if Attack was inverted, but AY usually drops to 0)
                envHold = true;
                envReverse = attackBit;
            } else {
                if (alternateBit) {
                    // Triangle wave logic
                    envReverse = !envReverse;
                    if (holdBit) {
                        envHold = true;
                        // After one alternate cycle, stay at the reached level
                        envReverse = (attackBit == alternateBit);
                    }
                } else {
                    if (holdBit) {
                        envHold = true;
                        envReverse = !attackBit;
                    } else {
                        // Standard repeat (sawtooth)
                        envReverse = false;
                    }
                }
            }
        }

        private int calculateEnvVolume() {
            int shape = activeRegs[13] & 0x0F;
            boolean attackBit = (shape & 0x04) != 0;

            int volume;
            if (envHold) {
                // If held, volume is constant based on final state
                volume = envReverse ? 0 : 31;
            } else {
                // If running, volume depends on current step and attack/alternate state
                volume = envReverse ? (31 - envStep) : envStep;
            }

            // If attack is 0 (falling), we invert the whole logic
            if (!attackBit) {
                volume = 31 - volume;
            }

            return volume >> 1; // Map 0..31 to 0..15
        }
    }
}