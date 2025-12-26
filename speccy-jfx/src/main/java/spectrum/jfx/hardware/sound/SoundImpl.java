package spectrum.jfx.hardware.sound;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.helper.AudioBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static spectrum.jfx.hardware.sound.SoundUtils.initializeAudio;

@Slf4j
public class SoundImpl implements Sound {

    public static final int BUFFER_SIZE = 1024;
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            SAMPLE_RATE, 16, 1, true, false
    );
    public static final long CPU_FREQUENCY = 3500000;  // 3.5 MHz
    public static final double CYCLES_PER_SAMPLE = (double) CPU_FREQUENCY / SAMPLE_RATE;

    @Setter
    @Getter
    private volatile boolean enabled = true;

    @Setter
    @Getter
    private volatile double volume = 0.8;

    private volatile boolean mute = false;

    private volatile boolean beeperState = false;
    private volatile double tactAccumulator = 0;
    private final AudioBuffer audioBuffer = new AudioBuffer(BUFFER_SIZE * 2);

    private SourceDataLine audioLine;

    private Thread audioThread;

    public SoundImpl() {

    }

    @Override
    public void init() {

    }

    @Override
    public void reset() {

    }

    @Override
    public void open() {
        enabled = true;
        audioLine = initializeAudio(AUDIO_FORMAT, BUFFER_SIZE * 2);
        if (audioLine == null) {
            enabled = false;
        }
        audioThread = new Thread(this::audioThreadCycle);
        audioThread.setDaemon(true);
        audioThread.start();
    }

    @Override
    public void close() {
        log.info("Stopping sound system");

        if (audioThread != null) {
            audioThread.interrupt();
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (audioLine != null) {
            audioLine.flush();
            audioLine.stop();
            audioLine.close();
        }

        log.info("Sound system stopped");
    }

    @Override
    public void outPort(int port, int value) {
        if ((port & 0xFF) != 0xFE) {
            return;
        }
        boolean newBeeperState = (value & 0x10) != 0;
        //boolean newBeeperState = ((value >> 3) & 1) != 0 || ((value >> 4) & 1) != 0;
        handleState(newBeeperState);
    }

    @Override
    public void pushBackTape(boolean state) {
        handleState(state);
    }

    @Override
    public void ticks(long tStates, int delta) {
        tactAccumulator += delta;
        generateSamples();
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        // ignore
    }

    @Override
    public void play(int cycles) {
        if (audioThread != null && audioThread.isAlive()) {
            return;
        }
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void endFrame() {

    }

    protected void handleState(boolean newBeeperState) {
        if (newBeeperState != beeperState) {
            generateSamples();
            beeperState = newBeeperState;
        }
    }

    protected void generateSamples() {
        while (tactAccumulator >= CYCLES_PER_SAMPLE) {
            writeSample(generateSample(beeperState, volume));
            tactAccumulator -= CYCLES_PER_SAMPLE;
        }
    }

    private void writeSample(short sample) {
        if (!audioBuffer.offer(sample)) {
            log.warn("Audio buffer overflow");
        }
    }

    private void audioThreadCycle() {
        byte[] playBuffer = new byte[BUFFER_SIZE * 2]; // 16-bit samples
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE * 2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        while (enabled) {

            try {
                byteBuffer.clear();

                for (int i = 0; i < BUFFER_SIZE; i++) {
                    short sample = getNextSample();
                    byteBuffer.putShort(sample);
                }

                if (mute) {
                    Thread.yield();
                    continue;
                }

                byteBuffer.rewind();
                byteBuffer.get(playBuffer);
                audioLine.write(playBuffer, 0, playBuffer.length);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in audio thread", e);
                break;
            }
        }
        log.debug("Audio thread stopped");
    }

    private short getNextSample() throws InterruptedException {
        return audioBuffer.take();
    }

    protected static short generateSample(boolean state, double volume) {
        if (state) {
            return (short) (Short.MAX_VALUE * volume);
        } else {
            return (short) (Short.MIN_VALUE * volume);
        }
    }

    @Override
    public void mute(boolean state) {
        this.mute = state;
    }

}
