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

    private static final int SAMPLE_RATE = 44100;
    private static final double MASTER_CLOCK = 1750000.0; // 1.75 MHz for ZX Spectrum

    // Logarithmic volume table (16 levels)
    private static final int[] VOL_TABLE = {
            0, 2, 3, 4, 6, 8, 11, 16, 23, 32, 45, 64, 90, 127, 180, 255
    };

    private final AYAudioEngine engine;
    private double globalVolume = 1.0;
    private boolean isMuted = false;

    public AY38912(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        this.engine = new AYAudioEngine();
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
            // Register 7 (Mixer) defaults to 0xFF (all channels off) on some systems,
            // but 0 is common for a clean state.
            regs[7] = 0x3F;
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
        // We don't necessarily stop the thread to allow quick resume,
        // but we could call engine.interrupt() if a hard stop is needed.
    }

    @Override
    public int inPort(int port) {
        // Partial decoding: Register usually read on 0xFFFD
        if ((port & 0xC002) == 0xC000) {
            return regs[selectedRegister] & 0xFF;
        }
        return 0xFF; // floating bus
    }

    @Override
    public void outPort(int port, int value) {
        int maskedPort = port & 0xC002;
        value &= 0xFF;

        if (maskedPort == 0xC000) { // 0xFFFD: Register selection
            selectedRegister = value & 0x0F;
        } else if (maskedPort == 0x8000) { // 0xBFFD: Data write
            writeRegister(selectedRegister, value);
        }
    }

    private synchronized void writeRegister(int reg, int value) {
        regs[reg] = value;
        engine.updateRegisters(regs);
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
        // ignore
    }

    @Override
    public void endFrame() {
        // ignore
    }

    @Override
    public void pushBackTape(boolean state) {
        // ignore
    }

    // --- Audio engine (DSP) ---

    private static class AYAudioEngine extends Thread {

        private final int[] activeRegs = new int[16];
        private SourceDataLine line;
        @Setter
        private volatile boolean paused = true;
        @Setter
        private double masterGain = 1.0;

        // Generators states
        private final double[] toneCounters = new double[3];
        private final boolean[] toneStates = new boolean[3];

        private double noiseCounter = 0;
        private int noiseSeed = 1;
        private boolean noiseState = false;

        public AYAudioEngine() {
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

            // 1. Noise Generator (Register 6)
            int noisePeriod = activeRegs[6] & 0x1F;
            if (noisePeriod == 0) noisePeriod = 1;

            // Noise frequency is MASTER_CLOCK / (16 * noisePeriod)
            double noiseStep = (MASTER_CLOCK / (16.0 * noisePeriod)) / SAMPLE_RATE;
            noiseCounter += noiseStep;

            if (noiseCounter >= 1.0) {
                noiseCounter -= 1.0;
                // 17-bit LFSR (Pseudo-random noise)
                if (((noiseSeed + 1) & 2) != 0) noiseState = !noiseState;
                if ((noiseSeed & 1) != 0) noiseSeed ^= 0x24000;
                noiseSeed >>= 1;
            }

            // 2. Tone Channels A, B, C
            int mixer = activeRegs[7];

            for (int i = 0; i < 3; i++) {
                int tonePeriod = (activeRegs[i * 2] | ((activeRegs[i * 2 + 1] & 0x0F) << 8));
                if (tonePeriod == 0) tonePeriod = 1;

                // Frequency = MASTER_CLOCK / (16 * tonePeriod)
                double toneStep = (MASTER_CLOCK / (16.0 * tonePeriod)) / SAMPLE_RATE;
                toneCounters[i] += toneStep;

                if (toneCounters[i] >= 1.0) {
                    toneCounters[i] -= 1.0;
                    toneStates[i] = !toneStates[i];
                }

                // Mixer logic: 0 = Enable, 1 = Disable
                boolean toneDisabled = (mixer & (1 << i)) != 0;
                boolean noiseDisabled = (mixer & (1 << (i + 3))) != 0;

                // Channel is active if (Tone is HIGH OR disabled) AND (Noise is HIGH OR disabled)
                boolean output = (toneStates[i] | toneDisabled) & (noiseState | noiseDisabled);

                if (output) {
                    int volIdx = activeRegs[8 + i] & 0x0F;
                    finalMix += VOL_TABLE[volIdx];
                }
            }

            // Apply Master Volume and Mute, then normalize to signed byte
            double mixed = (finalMix / 3.0) * masterGain;
            return (byte) (mixed - 128);
        }
    }
}