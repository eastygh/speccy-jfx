package spectrum.jfx.hardware.sound.ay;

import lombok.experimental.UtilityClass;

/**
 * Constants for AY-3-8912 Programmable Sound Generator.
 * <p>
 * The AY-3-8912 is a 3-voice programmable sound generator with envelope control,
 * designed by General Instrument in 1978.
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
@UtilityClass
public final class AyConstants {

    // ========== Port Addresses (ZX Spectrum 128K) ==========

    /**
     * Port mask for AY chip detection.
     * Bits 15,14 = 11, Bit 1 = 0 -> AY port
     */
    public static final int PORT_MASK = 0xC002;

    /**
     * Register select port (write) / Data read port (read).
     * Address: 0xFFFD (bits 15,14=11, bit 1=0, bit 0=1)
     */
    public static final int PORT_REGISTER_SELECT = 0xC000;

    /**
     * Data write port.
     * Address: 0xBFFD (bit 15=1, bit 14=0, bit 1=0, bit 0=1)
     */
    public static final int PORT_DATA_WRITE = 0x8000;

    // ========== Register Count ==========

    public static final int REGISTER_COUNT = 16;

    // ========== Timing Constants ==========

    /**
     * Tone generator divider (16:1).
     * Frequency = AY_Clock / (16 * Period)
     */
    public static final int TONE_DIVIDER = 16;

    /**
     * Envelope generator divider (256:1).
     * Period is scaled by 256 compared to tone generators.
     */
    public static final int ENVELOPE_DIVIDER = 256;

    // ========== Noise Generator ==========

    /**
     * Initial seed for 17-bit LFSR noise generator.
     * Per datasheet: polynomial is x^17 + x^3 + 1
     */
    public static final int NOISE_LFSR_SEED = 0x1FFFF;

    /**
     * Noise period mask (5-bit).
     */
    public static final int NOISE_PERIOD_MASK = 0x1F;

    // ========== Tone Generator ==========

    /**
     * Tone period mask for fine register (8-bit).
     */
    public static final int TONE_FINE_MASK = 0xFF;

    /**
     * Tone period mask for coarse register (4-bit).
     */
    public static final int TONE_COARSE_MASK = 0x0F;

    // ========== Mixer Register Bits ==========

    public static final int MIXER_TONE_A_DISABLE = 0x01;
    public static final int MIXER_TONE_B_DISABLE = 0x02;
    public static final int MIXER_TONE_C_DISABLE = 0x04;
    public static final int MIXER_NOISE_A_DISABLE = 0x08;
    public static final int MIXER_NOISE_B_DISABLE = 0x10;
    public static final int MIXER_NOISE_C_DISABLE = 0x20;
    public static final int MIXER_IO_A_OUTPUT = 0x40;
    public static final int MIXER_IO_B_OUTPUT = 0x80;

    /**
     * Default mixer value (all tone and noise disabled).
     */
    public static final int MIXER_DEFAULT = 0x3F;

    // ========== Amplitude Register ==========

    /**
     * Amplitude level mask (4-bit).
     */
    public static final int AMPLITUDE_LEVEL_MASK = 0x0F;

    /**
     * Envelope mode bit in amplitude register.
     */
    public static final int AMPLITUDE_ENVELOPE_MODE = 0x10;

    // ========== Envelope Shape Bits ==========

    public static final int ENVELOPE_CONTINUE = 0x08;
    public static final int ENVELOPE_ATTACK = 0x04;
    public static final int ENVELOPE_ALTERNATE = 0x02;
    public static final int ENVELOPE_HOLD = 0x01;

    /**
     * Envelope shape mask (4-bit).
     */
    public static final int ENVELOPE_SHAPE_MASK = 0x0F;

    // ========== I/O Ports (unused in ZX Spectrum) ==========

    public static final int IO_PORT_A_INDEX = 14;
    public static final int IO_PORT_B_INDEX = 15;

    // ========== Channel Constants ==========

    public static final int CHANNEL_COUNT = 3;
    public static final int CHANNEL_A = 0;
    public static final int CHANNEL_B = 1;
    public static final int CHANNEL_C = 2;

    // ========== Audio Constants ==========

    public static final int DEFAULT_SAMPLE_RATE = 44100;

    /**
     * Gain multiplier for AY output to reach audible levels.
     */
    public static final double AY_GAIN = 64.0;
}
