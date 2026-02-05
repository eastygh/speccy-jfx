package spectrum.hardware.sound.ay;

import static spectrum.hardware.sound.ay.AyConstants.NOISE_LFSR_SEED;

/**
 * Noise generator for AY-3-8912.
 * <p>
 * Uses a 17-bit Linear Feedback Shift Register (LFSR).
 * Polynomial: x^17 + x^3 + 1 (taps at bits 0 and 3).
 * <p>
 * The LFSR produces a pseudo-random sequence of 131071 (2^17 - 1) states
 * before repeating.
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class NoiseGenerator {

    private double counter;
    private int lfsr;
    private boolean output;

    public NoiseGenerator() {
        reset();
    }

    /**
     * Reset generator to initial state.
     */
    public void reset() {
        counter = 0;
        lfsr = NOISE_LFSR_SEED;
        output = false;
    }

    /**
     * Update noise generator by one sample step.
     *
     * @param step Step increment (baseStep / period)
     * @return Current output state
     */
    public boolean update(double step) {
        counter += step;
        if (counter >= 1.0) {
            counter -= 1.0;
            clockLfsr();
        }
        return output;
    }

    /**
     * Clock the LFSR one step.
     * <p>
     * Polynomial: bit_out = bit0 XOR bit3
     * New bit shifts in from bit 16.
     */
    private void clockLfsr() {
        int bit = (lfsr ^ (lfsr >> 3)) & 1;
        lfsr = (lfsr >> 1) | (bit << 16);
        output = (lfsr & 1) != 0;
    }

    /**
     * Get current output state without updating.
     *
     * @return Current output state
     */
    public boolean getOutput() {
        return output;
    }
}
