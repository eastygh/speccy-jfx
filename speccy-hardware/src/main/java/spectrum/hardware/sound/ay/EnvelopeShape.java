package spectrum.hardware.sound.ay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static spectrum.hardware.sound.ay.AyConstants.ENVELOPE_SHAPE_MASK;

/**
 * Envelope shapes for AY-3-8912.
 * <p>
 * The envelope shape is determined by 4 bits: Continue (C), Attack (A), Alternate (AL), Hold (H).
 * <p>
 * Shapes 0-7 behave the same as shapes 0,1,2,3 repeated (Continue bit is 0, so single cycle).
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
@Getter
@RequiredArgsConstructor
public enum EnvelopeShape {

    // Shapes 0-3: Single cycle without Continue (same behavior)
    // \___  - Decay to 0, hold at 0
    SHAPE_0(0, false, false, false, false, "\\___"),
    SHAPE_1(1, false, false, false, true, "\\___"),
    SHAPE_2(2, false, false, true, false, "\\___"),
    SHAPE_3(3, false, false, true, true, "\\___"),

    // Shapes 4-7: Single cycle attack without Continue (same behavior)
    // /___  - Attack to 15, then drop to 0, hold at 0
    SHAPE_4(4, false, true, false, false, "/___"),
    SHAPE_5(5, false, true, false, true, "/___"),
    SHAPE_6(6, false, true, true, false, "/___"),
    SHAPE_7(7, false, true, true, true, "/___"),

    // Shape 8: Continuous sawtooth decay
    // \\\\  - Repeating decay
    SHAPE_8(8, true, false, false, false, "\\\\\\\\"),

    // Shape 9: Single decay, hold at 0
    // \___  - Decay to 0, hold at 0
    SHAPE_9(9, true, false, false, true, "\\___"),

    // Shape 10: Triangle decay-attack
    // \/\/  - Alternating decay/attack
    SHAPE_10(10, true, false, true, false, "\\/\\/"),

    // Shape 11: Single decay, hold at max
    // \---  - Decay to 0, then jump to 15 and hold
    SHAPE_11(11, true, false, true, true, "\\---"),

    // Shape 12: Continuous sawtooth attack
    // ////  - Repeating attack
    SHAPE_12(12, true, true, false, false, "////"),

    // Shape 13: Single attack, hold at max
    // /---  - Attack to 15, hold at 15
    SHAPE_13(13, true, true, false, true, "/---"),

    // Shape 14: Triangle attack-decay
    // /\/\  - Alternating attack/decay
    SHAPE_14(14, true, true, true, false, "/\\/\\"),

    // Shape 15: Single attack, hold at 0
    // /___  - Attack to 15, then drop to 0 and hold
    SHAPE_15(15, true, true, true, true, "/___");

    private final int code;
    private final boolean continueFlag;
    private final boolean attack;
    private final boolean alternate;
    private final boolean hold;
    private final String waveform;

    private static final EnvelopeShape[] BY_CODE = new EnvelopeShape[16];

    static {
        for (EnvelopeShape shape : values()) {
            BY_CODE[shape.code] = shape;
        }
    }

    /**
     * Get shape from 4-bit code.
     *
     * @param code Envelope shape code (0-15)
     * @return EnvelopeShape enum
     */
    public static EnvelopeShape fromCode(int code) {
        return BY_CODE[code & ENVELOPE_SHAPE_MASK];
    }

    /**
     * Get initial envelope direction.
     *
     * @return 1 for rising (attack), -1 for falling (decay)
     */
    public int getInitialDirection() {
        return attack ? 1 : -1;
    }

    /**
     * Get initial envelope level.
     *
     * @return 0 for attack shapes, 15 for decay shapes
     */
    public int getInitialLevel() {
        return attack ? 0 : 15;
    }
}
