package spectrum.jfx.hardware.sound.ay;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Audio output handler for AY-3-8912.
 * <p>
 * Manages the javax.sound.sampled audio line and buffer.
 * Separates audio rendering concerns from chip emulation.
 */
@Slf4j
public class AyAudioOutput {

    private static final int BUFFER_SIZE = 4096;

    private final int sampleRate;
    private SourceDataLine line;
    private final byte[] audioBuffer = new byte[BUFFER_SIZE];
    private int audioPos;

    private double masterGain = 1.0;
    private boolean muted;
    private boolean enabled = true;

    /**
     * Create audio output handler.
     *
     * @param sampleRate Audio sample rate (e.g., 44100)
     */
    public AyAudioOutput(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Initialize and open audio output.
     */
    public void open() {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, BUFFER_SIZE);
            line.start();
            audioPos = 0;
            enabled = true;
        } catch (LineUnavailableException e) {
            log.error("AY audio init failed", e);
            enabled = false;
        }
    }

    /**
     * Close audio output and release resources.
     */
    public void close() {
        if (line != null) {
            line.drain();
            line.close();
            line = null;
        }
        enabled = false;
    }

    /**
     * Write stereo sample to buffer.
     *
     * @param left  Left channel amplitude
     * @param right Right channel amplitude
     */
    public void writeSample(double left, double right) {
        if (!enabled) {
            return;
        }

        if (muted) {
            left = right = 0;
        }

        left *= masterGain;
        right *= masterGain;

        short ls = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, left));
        short rs = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, right));

        audioBuffer[audioPos++] = (byte) (ls & 0xFF);
        audioBuffer[audioPos++] = (byte) ((ls >> 8) & 0xFF);
        audioBuffer[audioPos++] = (byte) (rs & 0xFF);
        audioBuffer[audioPos++] = (byte) ((rs >> 8) & 0xFF);

        if (audioPos >= audioBuffer.length) {
            flush();
        }
    }

    /**
     * Flush buffer to audio line.
     */
    public void flush() {
        if (audioPos > 0 && line != null && enabled) {
            line.write(audioBuffer, 0, audioPos);
            audioPos = 0;
        }
    }

    /**
     * Reset audio buffer.
     */
    public void reset() {
        audioPos = 0;
    }

    /**
     * Set master volume.
     *
     * @param volume Volume level (0.0 to 1.0)
     */
    public void setVolume(double volume) {
        this.masterGain = Math.max(0, Math.min(1, volume));
    }

    /**
     * Get current master volume.
     *
     * @return Volume level (0.0 to 1.0)
     */
    public double getVolume() {
        return masterGain;
    }

    /**
     * Set mute state.
     *
     * @param muted true to mute output
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * Check if output is muted.
     *
     * @return true if muted
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * Check if audio output is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
