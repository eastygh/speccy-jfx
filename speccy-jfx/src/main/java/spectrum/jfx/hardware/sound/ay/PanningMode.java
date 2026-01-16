package spectrum.jfx.hardware.sound.ay;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stereo panning modes for AY-3-8912 output.
 * <p>
 * The original AY chip is mono, but various computers implemented
 * different stereo configurations. The most common is ABC stereo
 * (A=left, B=center, C=right).
 */
@Getter
@RequiredArgsConstructor
public enum PanningMode {

    /**
     * Mono output (all channels centered).
     */
    MONO(
            new double[]{1.0, 1.0, 1.0},
            new double[]{1.0, 1.0, 1.0}
    ),

    /**
     * ABC Stereo (ZX Spectrum 128K default).
     * Channel A = Left, B = Center, C = Right.
     */
    ABC(
            new double[]{1.0, 0.5, 0.0},
            new double[]{0.0, 0.5, 1.0}
    ),

    /**
     * Wide ABC stereo with less center bleed.
     * This provides better stereo separation.
     */
    ABC_WIDE(
            new double[]{1.0, 0.2, 0.0},
            new double[]{0.0, 0.2, 1.0}
    ),

    /**
     * ACB Stereo (alternative arrangement).
     * Channel A = Left, C = Center, B = Right.
     */
    ACB(
            new double[]{1.0, 0.0, 0.5},
            new double[]{0.0, 1.0, 0.5}
    ),

    /**
     * BAC Stereo.
     * Channel B = Left, A = Center, C = Right.
     */
    BAC(
            new double[]{0.5, 1.0, 0.0},
            new double[]{0.5, 0.0, 1.0}
    );

    private final double[] leftPan;
    private final double[] rightPan;

    /**
     * Get left channel coefficient for AY channel.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return Left pan coefficient (0.0 to 1.0)
     */
    public double getLeftPan(int channel) {
        return leftPan[channel];
    }

    /**
     * Get right channel coefficient for AY channel.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return Right pan coefficient (0.0 to 1.0)
     */
    public double getRightPan(int channel) {
        return rightPan[channel];
    }
}
