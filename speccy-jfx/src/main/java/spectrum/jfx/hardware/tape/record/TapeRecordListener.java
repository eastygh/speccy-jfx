package spectrum.jfx.hardware.tape.record;

/**
 * Listener interface for tape recording events.
 * Receives notifications about recording progress and completion.
 */
public interface TapeRecordListener {

    /**
     * Called when pilot tone is detected, indicating start of a new block.
     *
     * @param pilotCount Number of pilot pulses detected
     */
    void onPilotDetected(int pilotCount);

    /**
     * Called when a complete byte has been recorded.
     *
     * @param byteValue The recorded byte value
     * @param byteIndex Index of the byte in current block
     */
    void onByteRecorded(int byteValue, int byteIndex);

    /**
     * Called when a complete block has been recorded.
     *
     * @param flagByte The flag byte (0x00 for header, 0xFF for data)
     * @param data     The recorded data including flag and checksum
     * @param valid    True if checksum is valid
     */
    void onBlockRecorded(int flagByte, byte[] data, boolean valid);

    /**
     * Called when recording encounters an error.
     *
     * @param state   Current state when error occurred
     * @param message Error description
     */
    void onRecordingError(RecordingState state, String message);

    /**
     * Called when recording is stopped.
     *
     * @param blocksRecorded Total number of blocks recorded
     */
    void onRecordingStopped(int blocksRecorded);
}
