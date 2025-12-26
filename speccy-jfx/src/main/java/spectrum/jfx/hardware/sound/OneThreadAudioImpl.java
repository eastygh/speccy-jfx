package spectrum.jfx.hardware.sound;

import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static spectrum.jfx.hardware.sound.SoundImpl.generateSample;
import static spectrum.jfx.hardware.sound.SoundUtils.initializeAudio;

@Slf4j
public class OneThreadAudioImpl implements Sound {

    private final AudioFormat audioFormat;
    private final double cyclesPerSample;

    private double tactAccumulator = 0;
    private final short[] beeperBuffer = new short[1024];
    private int beeperBufferIndex = 0;
    private double volume = 0.8;
    private boolean beeperState = false;
    private volatile boolean mute = false;
    private boolean enabled = true;

    private SourceDataLine audioLine;

    public OneThreadAudioImpl(MachineTypes machineType, int rate) {
        if (rate == 0) {
            rate = SAMPLE_RATE;
        }
        this.audioFormat = new AudioFormat(rate, 8, 1, true, false);
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
        audioLine = initializeAudio(audioFormat, beeperBuffer.length);
        if (audioLine == null) {
            enabled = false;
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
        byte[] playBuffer = new byte[beeperBuffer.length * 2]; // 16-bit samples
        ByteBuffer byteBuffer = ByteBuffer.allocate(beeperBuffer.length * 2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < beeperBufferIndex; i++) {
            byteBuffer.putShort(beeperBuffer[i]);
        }
        byteBuffer.rewind();
        byteBuffer.get(playBuffer);
        audioLine.write(playBuffer, 0, beeperBufferIndex);
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
