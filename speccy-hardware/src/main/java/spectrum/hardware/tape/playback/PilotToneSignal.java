package spectrum.hardware.tape.playback;

import spectrum.hardware.tape.TapeSignal;

import static spectrum.hardware.tape.TapeConstants.PILOT_PULSE;

/**
 * Generates a continuous pilot tone signal.
 * Used as default signal when no tape is loaded.
 */
public class PilotToneSignal implements TapeSignal {

    private final long pulseTStates;
    private final boolean initialLevel;
    private long startTStates = 0;
    private boolean motorOn = true;

    public PilotToneSignal() {
        this(PILOT_PULSE, true);
    }

    public PilotToneSignal(long pulseTStates, boolean initialLevel) {
        if (pulseTStates <= 0) {
            throw new IllegalArgumentException("pulseTStates must be > 0");
        }
        this.pulseTStates = pulseTStates;
        this.initialLevel = initialLevel;
    }

    @Override
    public void setMotor(boolean on, long currentTStates) {
        if (motorOn == on) {
            return;
        }
        motorOn = on;
        if (on) {
            startTStates = currentTStates;
        }
    }

    @Override
    public void setSectionIndex(int index) {
        // Not applicable for pilot tone
    }

    public void seekToTState(long tstates) {
        startTStates = tstates;
    }

    @Override
    public boolean earLevelAt(long tstates) {
        if (!motorOn) {
            return initialLevel;
        }
        long dt = tstates - startTStates;
        if (dt <= 0) {
            return initialLevel;
        }
        long pulsesPassed = dt / pulseTStates;
        boolean toggled = (pulsesPassed & 1L) != 0L;
        return toggled != initialLevel;
    }
}
