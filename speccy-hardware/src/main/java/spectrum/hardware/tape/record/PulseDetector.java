package spectrum.hardware.tape.record;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static spectrum.hardware.tape.TapeConstants.MIC_MASK;


/**
 * Detects MIC level transitions and measures pulse durations.
 * <p>
 * Monitors the MIC output bit from port 0xFE writes and calculates
 * the duration (in T-states) between level transitions.
 */
@Slf4j
public class PulseDetector {

    /**
     * Listener for detected pulses.
     */
    public interface PulseListener {
        /**
         * Called when a complete pulse is detected.
         *
         * @param duration Pulse duration in T-states
         * @param level    The level that just ended (true = high, false = low)
         */
        void onPulseDetected(int duration, boolean level);
    }

    private final PulseListener listener;

    @Getter
    private boolean currentLevel = false;
    private long lastTransitionTStates = 0;
    private boolean firstTransition = true;

    /**
     * Creates a new pulse detector.
     *
     * @param listener Listener to receive pulse events
     */
    public PulseDetector(PulseListener listener) {
        this.listener = listener;
    }

    /**
     * Processes an output port write to detect MIC level changes.
     *
     * @param value   The value written to port 0xFE
     * @param tStates Current T-state counter
     */
    public void processOutput(int value, long tStates) {
        boolean newLevel = (value & MIC_MASK) != 0;

        if (newLevel != currentLevel) {
            if (!firstTransition) {
                int duration = (int) (tStates - lastTransitionTStates);
                if (duration > 0) {
                    listener.onPulseDetected(duration, currentLevel);
                }
            } else {
                firstTransition = false;
            }

            currentLevel = newLevel;
            lastTransitionTStates = tStates;
        }
    }

    /**
     * Resets the detector state.
     * Call this when starting a new recording session.
     */
    public void reset() {
        currentLevel = false;
        lastTransitionTStates = 0;
        firstTransition = true;
    }

    /**
     * Gets the duration since the last transition.
     * Useful for detecting timeouts.
     *
     * @param currentTStates Current T-state counter
     * @return Duration in T-states since last transition
     */
    public long getDurationSinceLastTransition(long currentTStates) {
        return currentTStates - lastTransitionTStates;
    }
}
