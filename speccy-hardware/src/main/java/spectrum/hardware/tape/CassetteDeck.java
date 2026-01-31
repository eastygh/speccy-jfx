package spectrum.hardware.tape;

import spectrum.hardware.machine.Device;
import spectrum.hardware.sound.Sound;
import spectrum.hardware.tape.events.CassetteDeckEvent;
import spectrum.hardware.tape.model.TapeFile;
import spectrum.hardware.tape.record.TapeRecordListener;
import spectrum.hardware.tape.tap.TapBlock;

import java.io.IOException;
import java.util.List;

/**
 * Cassette deck interface for tape playback and recording.
 * <p>
 * Emulates the ZX Spectrum tape interface:
 * - EAR (bit 6): Reading from tape via inPort
 * - MIC (bit 3): Writing to tape via outPort
 */
public interface CassetteDeck extends Device {

    // ========== Playback ==========

    /**
     * Sets the tape motor state for playback.
     *
     * @param on true to start motor, false to stop
     */
    void setMotor(boolean on);

    /**
     * Inserts a tape file for playback.
     *
     * @param tape Tape file to insert
     */
    void insertTape(TapeFile tape);

    /**
     * Sets the current section index for playback.
     *
     * @param index Section index
     */
    void setSectionIndex(int index);

    // ========== Recording ==========

    /**
     * Starts recording from MIC output.
     */
    void startRecording();

    /**
     * Stops recording.
     */
    void stopRecording();

    /**
     * Checks if currently recording.
     *
     * @return true if recording is active
     */
    boolean isRecording();

    /**
     * Gets the number of blocks recorded.
     *
     * @return Block count
     */
    int getRecordedBlockCount();

    /**
     * Gets the recorded blocks.
     *
     * @return List of recorded TapBlocks
     */
    List<TapBlock> getRecordedBlocks();

    /**
     * Saves recorded data to a TAP file.
     *
     * @param filePath Path to save to
     * @throws IOException If save fails
     */
    void saveRecordedTape(String filePath) throws IOException;

    /**
     * Clears all recorded data.
     */
    void clearRecording();

    // ========== Events ==========

    /**
     * Adds a cassette deck event listener.
     *
     * @param listener Listener to add
     */
    void addCassetteDeckEventListener(CassetteDeckEvent listener);

    /**
     * Adds a recording event listener.
     *
     * @param listener Listener to add
     */
    void addRecordListener(TapeRecordListener listener);

    /**
     * Removes a recording event listener.
     *
     * @param listener Listener to remove
     */
    void removeRecordListener(TapeRecordListener listener);

    // ========== Sound ==========

    /**
     * Sets the sound output for tape audio feedback.
     *
     * @param sound Sound instance
     */
    void setSound(Sound sound);

    /**
     * Enables/disables sound pushback during playback.
     *
     * @param pushBack true to enable
     */
    void setSoundPushBack(boolean pushBack);
}
