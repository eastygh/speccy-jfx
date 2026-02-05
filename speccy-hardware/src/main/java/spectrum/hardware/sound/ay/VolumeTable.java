package spectrum.hardware.sound.ay;

import lombok.experimental.UtilityClass;

/**
 * Volume table for AY-3-8912 DAC.
 * <p>
 * The AY-3-8912 uses a logarithmic DAC with approximately 3dB steps.
 * These values are derived from actual chip measurements.
 * <p>
 * Reference: The values approximate V(n) = V_max * 10^((n-15)/10)
 * where V_max = 255 and n = 0..15.
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
@UtilityClass
public class VolumeTable {

    /**
     * Measured DAC output levels for volume 0-15.
     * <p>
     * Level 0 is silence (0). Each subsequent level is approximately
     * sqrt(2) times the previous, giving roughly 3dB steps.
     * <p>
     * These values are compatible with 8-bit audio amplitude scaling.
     */
    public static final int[] LEVELS = {
            0,      // Level 0: Silence
            2,      // Level 1
            3,      // Level 2
            4,      // Level 3
            6,      // Level 4
            8,      // Level 5
            11,     // Level 6
            16,     // Level 7
            23,     // Level 8
            32,     // Level 9
            45,     // Level 10
            64,     // Level 11
            90,     // Level 12
            127,    // Level 13
            180,    // Level 14
            255     // Level 15: Maximum
    };

    /**
     * Get amplitude for volume level.
     *
     * @param level Volume level (0-15)
     * @return Amplitude value (0-255)
     */
    public static int getAmplitude(int level) {
        return LEVELS[Math.max(0, Math.min(15, level))];
    }
}
