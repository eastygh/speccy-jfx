package spectrum.hardware.tape.record;

import lombok.Getter;

/**
 * Accumulates bits into bytes in MSB-first order.
 * <p>
 * ZX Spectrum tape format transmits data MSB first,
 * with each bit encoded as a pair of pulses.
 */
public class ByteAccumulator {

    /**
     * Listener for completed bytes.
     */
    public interface ByteListener {
        /**
         * Called when a complete byte has been accumulated.
         *
         * @param value The byte value (0-255)
         */
        void onByteComplete(int value);
    }

    private final ByteListener listener;

    @Getter
    private int currentByte = 0;
    @Getter
    private int bitPosition = 7;  // Start from MSB
    private int pulseCount = 0;   // Count pulses for current bit
    @Getter
    private int lastBitValue = -1;

    /**
     * Creates a new byte accumulator.
     *
     * @param listener Listener to receive completed bytes
     */
    public ByteAccumulator(ByteListener listener) {
        this.listener = listener;
    }

    /**
     * Adds a bit value to the accumulator.
     * Each bit requires two pulses of the same duration.
     *
     * @param bitValue The bit value (0 or 1)
     * @return true if this completes a byte
     */
    public boolean addBit(int bitValue) {
        pulseCount++;

        // First pulse of the bit pair - just record the value
        if (pulseCount == 1) {
            lastBitValue = bitValue;
            return false;
        }

        // Second pulse - verify it matches and add the bit
        pulseCount = 0;

        if (bitValue != lastBitValue) {
            // Mismatch between pulse pair - use the first value
            // This handles minor timing variations
        }

        if (lastBitValue != 0) {
            currentByte |= (1 << bitPosition);
        }

        bitPosition--;
        lastBitValue = -1;

        if (bitPosition < 0) {
            int completedByte = currentByte;
            reset();
            listener.onByteComplete(completedByte);
            return true;
        }

        return false;
    }

    /**
     * Resets the accumulator for a new byte.
     */
    public void reset() {
        currentByte = 0;
        bitPosition = 7;
        pulseCount = 0;
        lastBitValue = -1;
    }

    /**
     * Checks if currently in the middle of a bit (waiting for second pulse).
     *
     * @return true if waiting for second pulse of a bit
     */
    public boolean isWaitingForSecondPulse() {
        return pulseCount == 1;
    }
}
