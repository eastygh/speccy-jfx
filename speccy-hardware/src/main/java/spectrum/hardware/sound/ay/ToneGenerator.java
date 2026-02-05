package spectrum.hardware.sound.ay;

/**
 * Tone generator for a single AY-3-8912 channel.
 * <p>
 * The tone generator uses a 12-bit period counter. Output toggles when counter reaches zero.
 * Frequency = AY_Clock / (16 * Period)
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class ToneGenerator {

    private double counter;
    private boolean output;

    /**
     * Reset generator to initial state.
     */
    public void reset() {
        counter = 0;
        output = false;
    }

    /**
     * Update tone generator by one sample step.
     *
     * @param step Step increment (baseStep / period)
     * @return Current output state (true = high, false = low)
     */
    public boolean update(double step) {
        counter += step;
        if (counter >= 1.0) {
            counter -= 1.0;
            output = !output;
        }
        return output;
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
