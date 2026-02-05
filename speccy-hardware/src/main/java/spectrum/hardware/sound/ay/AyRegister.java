package spectrum.hardware.sound.ay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AY-3-8912 register definitions.
 * <p>
 * Registers R0-R13 are read/write. R14-R15 are I/O ports (not used in ZX Spectrum 128K).
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
@Getter
@RequiredArgsConstructor
public enum AyRegister {

    // Tone Period Registers (12-bit, split into fine/coarse)
    TONE_A_FINE(0, 0xFF, "Channel A Tone Period Fine"),
    TONE_A_COARSE(1, 0x0F, "Channel A Tone Period Coarse"),
    TONE_B_FINE(2, 0xFF, "Channel B Tone Period Fine"),
    TONE_B_COARSE(3, 0x0F, "Channel B Tone Period Coarse"),
    TONE_C_FINE(4, 0xFF, "Channel C Tone Period Fine"),
    TONE_C_COARSE(5, 0x0F, "Channel C Tone Period Coarse"),

    // Noise Period (5-bit)
    NOISE_PERIOD(6, 0x1F, "Noise Period"),

    // Mixer Control
    MIXER(7, 0xFF, "Mixer Control (Tone/Noise Enable)"),

    // Amplitude Control (4-bit level + envelope mode bit)
    AMPLITUDE_A(8, 0x1F, "Channel A Amplitude"),
    AMPLITUDE_B(9, 0x1F, "Channel B Amplitude"),
    AMPLITUDE_C(10, 0x1F, "Channel C Amplitude"),

    // Envelope Period (16-bit)
    ENVELOPE_FINE(11, 0xFF, "Envelope Period Fine"),
    ENVELOPE_COARSE(12, 0xFF, "Envelope Period Coarse"),

    // Envelope Shape (4-bit CAAH)
    ENVELOPE_SHAPE(13, 0x0F, "Envelope Shape (CAAH)"),

    // I/O Ports (unused in ZX Spectrum)
    IO_PORT_A(14, 0xFF, "I/O Port A Data"),
    IO_PORT_B(15, 0xFF, "I/O Port B Data");

    private final int index;
    private final int mask;
    private final String description;

    private static final AyRegister[] BY_INDEX = new AyRegister[16];

    static {
        for (AyRegister reg : values()) {
            BY_INDEX[reg.index] = reg;
        }
    }

    /**
     * Get register by index.
     *
     * @param index Register index (0-15)
     * @return Register enum or null if invalid
     */
    public static AyRegister fromIndex(int index) {
        if (index < 0 || index >= BY_INDEX.length) {
            return null;
        }
        return BY_INDEX[index];
    }

    /**
     * Apply mask to value.
     *
     * @param value Raw value to mask
     * @return Masked value
     */
    public int masked(int value) {
        return value & mask;
    }

    /**
     * Check if this is a readable register.
     * R0-R13 are readable, R14-R15 (I/O ports) return 0xFF when not connected.
     */
    public boolean isReadable() {
        return index < 14;
    }

    /**
     * Get the channel index for tone/amplitude registers.
     *
     * @return Channel index (0=A, 1=B, 2=C) or -1 if not a channel register
     */
    public int getChannelIndex() {
        return switch (this) {
            case TONE_A_FINE, TONE_A_COARSE, AMPLITUDE_A -> 0;
            case TONE_B_FINE, TONE_B_COARSE, AMPLITUDE_B -> 1;
            case TONE_C_FINE, TONE_C_COARSE, AMPLITUDE_C -> 2;
            default -> -1;
        };
    }
}
