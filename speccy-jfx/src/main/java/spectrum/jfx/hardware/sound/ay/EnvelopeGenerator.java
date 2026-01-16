package spectrum.jfx.hardware.sound.ay;

/**
 * Envelope generator for AY-3-8912.
 * <p>
 * The envelope is a 4-bit counter (0-15) that can rise, fall, or hold
 * based on the selected shape (CAAH bits: Continue, Attack, Alternate, Hold).
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class EnvelopeGenerator {

    private double counter;
    private int level;
    private int direction;
    private boolean hold;
    private EnvelopeShape shape;

    public EnvelopeGenerator() {
        reset();
    }

    /**
     * Reset generator to initial state.
     */
    public void reset() {
        counter = 0;
        level = 0;
        direction = 1;
        hold = false;
        shape = EnvelopeShape.SHAPE_0;
    }

    /**
     * Set envelope shape and reset generator state.
     * <p>
     * This is called when register R13 (envelope shape) is written.
     *
     * @param shapeCode Envelope shape code (0-15)
     */
    public void setShape(int shapeCode) {
        shape = EnvelopeShape.fromCode(shapeCode);
        counter = 0;
        hold = false;
        direction = shape.getInitialDirection();
        level = shape.getInitialLevel();
    }

    /**
     * Update envelope generator by one sample step.
     *
     * @param step Step increment (envBaseStep / period)
     * @return Current envelope level (0-15)
     */
    public int update(double step) {
        if (hold) {
            return level;
        }

        counter += step;
        if (counter >= 1.0) {
            counter -= 1.0;
            advanceLevel();
        }
        return level;
    }

    /**
     * Advance envelope level by one step.
     */
    private void advanceLevel() {
        level += direction;

        if (level < 0 || level > 15) {
            handleBoundary();
        }
    }

    /**
     * Handle envelope reaching boundary (0 or 15).
     */
    private void handleBoundary() {
        if (!shape.isContinueFlag()) {
            // Non-continuous: hold at 0
            hold = true;
            level = 0;
            return;
        }

        if (shape.isAlternate()) {
            // Reverse direction
            direction = -direction;
            level += direction;

            if (shape.isHold()) {
                hold = true;
            }
        } else {
            if (shape.isHold()) {
                // Hold at opposite extreme
                hold = true;
                level = shape.isAttack() ? 15 : 0;
            } else {
                // Restart cycle
                level = shape.getInitialLevel();
            }
        }
    }

    /**
     * Get current envelope level (clamped to 0-15).
     *
     * @return Current level
     */
    public int getLevel() {
        return Math.max(0, Math.min(15, level));
    }
}
