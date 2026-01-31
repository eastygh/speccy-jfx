package spectrum.hardware.tape.record;

/**
 * State machine states for tape recording/decoding.
 * Controls the sequence of pulse detection during tape saving.
 */
public enum RecordingState {

    /**
     * Waiting for recording to start, looking for pilot tone.
     */
    IDLE,

    /**
     * Detecting pilot tone pulses.
     */
    PILOT,

    /**
     * Waiting for first sync pulse (667 T-states).
     */
    SYNC1,

    /**
     * Waiting for second sync pulse (735 T-states).
     */
    SYNC2,

    /**
     * Decoding data bits.
     */
    DATA,

    /**
     * Block completed, waiting for next pilot or end.
     */
    BLOCK_COMPLETE,

    /**
     * Error state - invalid pulse detected.
     */
    ERROR
}
