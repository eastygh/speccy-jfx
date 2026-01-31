package spectrum.hardware.tape.playback;

/**
 * State machine states for tape playback.
 * Controls the sequence of pulse generation during tape loading.
 */
public enum TapePlaybackState {

    /**
     * Pause between blocks.
     */
    PAUSE,

    /**
     * Playing pilot tone (leader tone).
     */
    PILOT,

    /**
     * First sync pulse after pilot.
     */
    SYNC1,

    /**
     * Second sync pulse.
     */
    SYNC2,

    /**
     * Playing data bits.
     */
    DATA,

    /**
     * Final sync pulse after data block.
     */
    FINAL_SYNC
}
