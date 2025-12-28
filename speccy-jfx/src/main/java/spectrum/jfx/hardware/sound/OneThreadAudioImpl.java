package spectrum.jfx.hardware.sound;

import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static spectrum.jfx.hardware.sound.SoundUtils.initializeAudio;

@Slf4j
public class OneThreadAudioImpl implements Sound {

    private final AudioFormat audioFormat;
    private final double cyclesPerSample;

    private double tactAccumulator = 0;
    private final short[] beeperBuffer = new short[1024];
    private int beeperBufferIndex = 0;
    private double volume = 0.8;
    private double lastSample = 0;
    private double lastFiltered = 0;
    private boolean beeperState = false;
    private volatile boolean mute = false;
    private boolean enabled = true;

    private SourceDataLine audioLine;

    public OneThreadAudioImpl(MachineTypes machineType, int rate) {
        if (rate == 0) {
            rate = SAMPLE_RATE;
        }
        this.audioFormat = new AudioFormat(rate, 16, 1, true, false);
        this.cyclesPerSample = (double) machineType.clockFreq / rate;
    }

    @Override
    public double getVolume() {
        return this.volume;
    }

    @Override
    public void setVolume(double volume) {
        this.volume = Math.clamp(volume, 0d, 1d);
    }

    @Override
    public synchronized void open() {
        tactAccumulator = 0;
        enabled = true;
        audioLine = initializeAudio(audioFormat, beeperBuffer.length * 4);
        if (audioLine == null) {
            enabled = false;
        } else {
            audioLine.start();
        }
    }

    @Override
    public void init() {
        tactAccumulator = 0;
    }

    @Override
    public void reset() {
        tactAccumulator = 0;
    }

    @Override
    public void close() {
        audioLine.drain();
        audioLine.stop();
        audioLine.close();
        audioLine = null;
    }

    @Override
    public void pushBackTape(boolean state) {
        handleState(state);
    }

    @Override
    public void play(int cycles) {
        // ignore
    }

    @Override
    public synchronized void endFrame() {
        if (!enabled || mute) {
            beeperBufferIndex = 0;
            return;
        }
        int bytesToWrite = beeperBufferIndex * 2;
        byte[] playBuffer = new byte[bytesToWrite];
        ByteBuffer byteBuffer = ByteBuffer.wrap(playBuffer);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < beeperBufferIndex; i++) {
            byteBuffer.putShort(beeperBuffer[i]);
        }
        audioLine.write(playBuffer, 0, beeperBufferIndex * 2);
        beeperBufferIndex = 0;
    }

    @Override
    public void ticks(long tStates, int delta) {
        tactAccumulator += delta;
        generateSamples();
    }

    @Override
    public void mute(boolean mute) {
        this.mute = mute;
    }

    protected void generateSamples() {
        while (tactAccumulator >= cyclesPerSample) {
            writeSample(generateSample(beeperState, volume));
            tactAccumulator -= cyclesPerSample;
        }
    }

    protected short generateSample(boolean state, double volume) {
        double rawSample = state ? BEEPER_AMPLITUDE : 0;
        return applyFilters(rawSample, volume);
    }

    private short applyFilters(double rawInput, double volume) {
        // DC Blocker
        double dcFiltered = rawInput - lastSample + (0.995 * lastFiltered);

        lastSample = rawInput;
        lastFiltered = dcFiltered;

        double finalSample = dcFiltered * volume;

        // Clamping (overflow protect)
        return (short) Math.clamp(finalSample, -32767, 32767);
    }

    private synchronized void writeSample(short sample) {
        if (beeperBufferIndex >= beeperBuffer.length) {
            log.error("Beeper buffer overflow.");
        }
        beeperBuffer[beeperBufferIndex++] = sample;
        if (beeperBufferIndex >= beeperBuffer.length) {
            beeperBufferIndex = 0;
        }
    }

    @Override
    public void outPort(int port, int value) {
        if ((port & 0xFF) != 0xFE) {
            return;
        }
        boolean newBeeperState = (value & 0x10) != 0;
        handleState(newBeeperState);
    }

    private void handleState(boolean newBeeperState) {
        if (newBeeperState != beeperState) {
            generateSamples();
            beeperState = newBeeperState;
        }
    }

}
