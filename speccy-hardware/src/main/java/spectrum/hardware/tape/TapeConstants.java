package spectrum.hardware.tape;

import lombok.experimental.UtilityClass;

/**
 * ZX Spectrum tape timing constants in T-states.
 * <p>
 * Based on official ZX Spectrum tape format specification.
 * A T-state at 3.5MHz is approximately 286 nanoseconds.
 *
 * @see <a href="https://sinclair.wiki.zxnet.co.uk/wiki/Spectrum_tape_interface">Spectrum tape interface</a>
 */
@UtilityClass
public final class TapeConstants {

    // ========== Port 0xFE Bit Masks ==========

    /**
     * Port 0xFE address.
     */
    public static final int PORT_FE = 0xFE;

    /**
     * EAR input bit position (bit 6).
     */
    public static final int EAR_BIT = 6;

    /**
     * EAR input mask (0b0100_0000).
     * Reading tape input from port 0xFE.
     */
    public static final int EAR_MASK = 0x40;

    /**
     * MIC output bit position (bit 3).
     */
    public static final int MIC_BIT = 3;

    /**
     * MIC output mask (0b0000_1000).
     * Writing tape output to port 0xFE.
     */
    public static final int MIC_MASK = 0x08;

    // ========== Pulse Durations (T-states) ==========

    /**
     * Pilot tone pulse duration.
     * Standard ROM save routine uses this for leader tone.
     */
    public static final int PILOT_PULSE = 2168;

    /**
     * First sync pulse duration after pilot tone.
     */
    public static final int SYNC1_PULSE = 667;

    /**
     * Second sync pulse duration.
     */
    public static final int SYNC2_PULSE = 735;

    /**
     * Pulse duration for bit value 0.
     * Two pulses of this length encode a single 0 bit.
     */
    public static final int ZERO_PULSE = 855;

    /**
     * Pulse duration for bit value 1.
     * Two pulses of this length encode a single 1 bit.
     */
    public static final int ONE_PULSE = 1710;

    /**
     * Final sync pulse after data.
     */
    public static final int FINAL_SYNC_PULSE = 945;

    /**
     * Pause duration between blocks (approximately 1 second).
     */
    public static final int PAUSE_DURATION = 350000;

    // ========== Pilot Pulse Counts ==========

    /**
     * Number of pilot pulses before header block.
     * Standard ROM uses 8063, but some implementations use 4123.
     */
    public static final int PILOT_HEADER_COUNT = 8063;

    /**
     * Actual pilot count used in current implementation for headers.
     */
    public static final int PILOT_HEADER_COUNT_ACTUAL = 4123;

    /**
     * Number of pilot pulses before data block.
     */
    public static final int PILOT_DATA_COUNT = 3223;

    // ========== Detection Tolerance ==========

    /**
     * Tolerance percentage for pulse detection (20%).
     */
    public static final double PULSE_TOLERANCE = 0.20;

    /**
     * Minimum pilot pulse duration.
     */
    public static final int PILOT_MIN = (int) (PILOT_PULSE * (1 - PULSE_TOLERANCE));

    /**
     * Maximum pilot pulse duration.
     */
    public static final int PILOT_MAX = (int) (PILOT_PULSE * (1 + PULSE_TOLERANCE));

    /**
     * Minimum sync1 pulse duration.
     */
    public static final int SYNC1_MIN = (int) (SYNC1_PULSE * (1 - PULSE_TOLERANCE));

    /**
     * Maximum sync1 pulse duration.
     */
    public static final int SYNC1_MAX = (int) (SYNC1_PULSE * (1 + PULSE_TOLERANCE));

    /**
     * Minimum sync2 pulse duration.
     */
    public static final int SYNC2_MIN = (int) (SYNC2_PULSE * (1 - PULSE_TOLERANCE));

    /**
     * Maximum sync2 pulse duration.
     */
    public static final int SYNC2_MAX = (int) (SYNC2_PULSE * (1 + PULSE_TOLERANCE));

    /**
     * Minimum zero bit pulse duration.
     */
    public static final int ZERO_MIN = (int) (ZERO_PULSE * (1 - PULSE_TOLERANCE));

    /**
     * Maximum zero bit pulse duration.
     */
    public static final int ZERO_MAX = (int) (ZERO_PULSE * (1 + PULSE_TOLERANCE));

    /**
     * Minimum one bit pulse duration.
     */
    public static final int ONE_MIN = (int) (ONE_PULSE * (1 - PULSE_TOLERANCE));

    /**
     * Maximum one bit pulse duration.
     */
    public static final int ONE_MAX = (int) (ONE_PULSE * (1 + PULSE_TOLERANCE));

    /**
     * Threshold between 0 and 1 bit pulse durations.
     * Pulses shorter than this are 0, longer are 1.
     */
    public static final int BIT_THRESHOLD = (ZERO_PULSE + ONE_PULSE) / 2;

    /**
     * Minimum number of pilot pulses to consider valid pilot tone.
     */
    public static final int MIN_PILOT_PULSES = 256;

    // ========== TAP File Format ==========

    /**
     * Flag byte for header blocks in TAP format.
     */
    public static final int TAP_FLAG_HEADER = 0x00;

    /**
     * Flag byte for data blocks in TAP format.
     */
    public static final int TAP_FLAG_DATA = 0xFF;

    /**
     * Standard header block length (17 bytes + flag + checksum = 19).
     */
    public static final int TAP_HEADER_LENGTH = 19;

    // ========== Header Types ==========

    /**
     * Header type: BASIC program.
     */
    public static final int HEADER_TYPE_PROGRAM = 0;

    /**
     * Header type: Number array.
     */
    public static final int HEADER_TYPE_NUMBER_ARRAY = 1;

    /**
     * Header type: Character array.
     */
    public static final int HEADER_TYPE_CHAR_ARRAY = 2;

    /**
     * Header type: Code/bytes.
     */
    public static final int HEADER_TYPE_CODE = 3;
}
