package spectrum.jfx.hardware.sound;

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

    private static final int BUFFER_SIZE = 4096;
    private static final double AY_GAIN = 64.0; // корректная амплитуда для 16-bit

    private static final int[] VOL_TABLE = {
            0, 2, 3, 4, 6, 8, 11, 16,
            23, 32, 45, 64, 90, 127, 180, 255
    };

    private static final double[] PAN_L = {1.0, 0.2, 0.0};
    private static final double[] PAN_R = {0.0, 0.2, 1.0};

    private final int[] regs = new int[16];
    private int selectedRegister;

    private final double ayClock;
    private final double tStatesPerSample;

    private final double[] toneCounter = new double[3];
    private final boolean[] toneState = new boolean[3];

    private double noiseCounter;
    private int noiseSeed = 0x1FFFF;
    private boolean noiseState;

    private double envCounter;
    private int envStep;
    private int envDir = 1;
    private boolean envHold;

    private volatile boolean enabled = true;
    private volatile boolean speedUpMode = false;

    private SourceDataLine line;
    private final byte[] audioBuffer = new byte[BUFFER_SIZE];
    private int audioPos;

    private double tStateAcc;
    private double masterGain = 1.0;
    private boolean muted;

    public AY38912(MachineSettings settings) {
        this.ayClock = settings.getMachineType().clockFreq / 2.0;
        this.tStatesPerSample =
                settings.getMachineType().clockFreq / (double) SAMPLE_RATE;
        initAudio();
    }

    private void initAudio() {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, BUFFER_SIZE);
            line.start();
        } catch (LineUnavailableException e) {
            log.error("AY audio init failed", e);
            enabled = false;
        }
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        Arrays.fill(regs, 0);
        regs[7] = 0x3F;
        Arrays.fill(toneCounter, 0);
        Arrays.fill(toneState, false);
        noiseCounter = 0;
        noiseSeed = 0x1FFFF;
        noiseState = false;
        envCounter = 0;
        envStep = 0;
        envDir = 1;
        envHold = false;
        audioPos = 0;
        tStateAcc = 0;
    }

    @Override
    public void open() {
        // ignored
    }

    @Override
    public void close() {
        if (line != null) {
            line.drain();
            line.close();
            line = null;
        }
        enabled = false;
    }

    @Override
    public int inPort(int port) {
        if ((port & 0xC002) == 0xC000) {
            if (selectedRegister == 14) {
                return 0xFF;
            } else {
                return regs[selectedRegister] & 0xFF;
            }
        }
        return 0xFF;
    }

    @Override
    public void outPort(int port, int value) {
        value &= 0xFF;
        int p = port & 0xC002;
        if (p == 0xC000) {
            selectedRegister = value & 0x0F;
        } else if (p == 0x8000) {
            writeReg(selectedRegister, value);
        }
    }

    private void writeReg(int r, int v) {
        regs[r] = v;
        if (r == 13) {
            envCounter = 0;
            envStep = (v & 0x04) != 0 ? 0 : 15;
            envDir = (v & 0x04) != 0 ? 1 : -1;
            envHold = false;
        }
    }

    @Override
    public void ticks(long tStates, int delta) {
        if (speedUpMode || !enabled) {
            return;
        }
        tStateAcc += delta;
        while (tStateAcc >= tStatesPerSample) {
            tStateAcc -= tStatesPerSample;
            renderSample();
        }
    }

    @Override
    public void setSpeedUpMode(boolean speedUpMode) {
        this.speedUpMode = speedUpMode;
    }

    @Override
    public void endFrame() {
        flush();
    }

    private void renderSample() {
        updateNoise();
        updateEnvelope();
        double left = 0;
        double right = 0;
        int mixer = regs[7];

        for (int ch = 0; ch < 3; ch++) {
            updateTone(ch);
            boolean toneEnabled = (mixer & (1 << ch)) == 0;
            boolean noiseEnabled = (mixer & (1 << (ch + 3))) == 0;
            boolean t = !toneEnabled || toneState[ch];
            boolean n = !noiseEnabled || noiseState;
            if (t && n) {
                int vr = regs[8 + ch];
                int vol = (vr & 0x10) != 0 ? getEnvVolume() : (vr & 0x0F);
                double amp = VOL_TABLE[vol] * AY_GAIN;
                left += amp * PAN_L[ch];
                right += amp * PAN_R[ch];
            }
        }

        writeSample(left, right);
    }

    private void updateTone(int ch) {
        int p = regs[ch * 2] | ((regs[ch * 2 + 1] & 0x0F) << 8);
        if (p == 0) p = 1;
        toneCounter[ch] += (ayClock / (16.0 * p)) / SAMPLE_RATE;
        if (toneCounter[ch] >= 1.0) {
            toneCounter[ch] -= 1.0;
            toneState[ch] = !toneState[ch];
        }
    }

    private void updateNoise() {
        int p = regs[6] & 0x1F;
        if (p == 0) p = 1;
        noiseCounter += (ayClock / (16.0 * p)) / SAMPLE_RATE;
        if (noiseCounter >= 1.0) {
            noiseCounter -= 1.0;
            int bit = (noiseSeed ^ (noiseSeed >> 3)) & 1;
            noiseSeed = (noiseSeed >> 1) | (bit << 16);
            noiseState = (noiseSeed & 1) != 0;
        }
    }

    private void updateEnvelope() {
        int p = regs[11] | (regs[12] << 8);
        if (p == 0) p = 1;
        envCounter += (ayClock / (256.0 * p)) / SAMPLE_RATE;
        if (envCounter >= 1.0 && !envHold) {
            envCounter -= 1.0;
            envStep += envDir;
            if (envStep < 0 || envStep > 15) handleEnvelopeShape();
        }
    }

    private void handleEnvelopeShape() {
        int shape = regs[13] & 0x0F;
        boolean cont = (shape & 0x08) != 0;
        boolean alt = (shape & 0x02) != 0;
        boolean hold = (shape & 0x01) != 0;

        if (!cont) {
            envHold = true;
            envStep = envDir > 0 ? 15 : 0;
            return;
        }
        if (alt) envDir = -envDir;
        if (hold) envHold = true;
        envStep = envDir > 0 ? 0 : 15;
    }

    private int getEnvVolume() {
        return Math.clamp(envStep, 0, 15);
    }

    private void writeSample(double l, double r) {
        if (muted) {
            l = r = 0;
        }
        l *= masterGain;
        r *= masterGain;
        short ls = (short) Math.clamp(l, Short.MIN_VALUE, Short.MAX_VALUE);
        short rs = (short) Math.clamp(r, Short.MIN_VALUE, Short.MAX_VALUE);
        audioBuffer[audioPos++] = (byte) ls;
        audioBuffer[audioPos++] = (byte) (ls >> 8);
        audioBuffer[audioPos++] = (byte) rs;
        audioBuffer[audioPos++] = (byte) (rs >> 8);
        if (audioPos >= audioBuffer.length) {
            flush();
        }
    }

    private void flush() {
        if (audioPos > 0) {
            line.write(audioBuffer, 0, audioPos);
            audioPos = 0;
        }
    }

    @Override
    public double getVolume() {
        return masterGain;
    }

    @Override
    public void setVolume(double v) {
        masterGain = Math.clamp(v, 0, 1);
    }

    @Override
    public void mute(boolean state) {
        muted = state;
    }

    @Override
    public void play(int cycles) {
        // ignored
    }

    @Override
    public void pushBackTape(boolean state) {
        // ignored
    }
}
