package spectrum.hardware.tape.playback;

import spectrum.hardware.tape.TapeSignal;

import java.util.Random;

/**
 * Generates a silent/noise signal simulating empty tape.
 * Produces random level changes to simulate tape noise.
 */
public class SilentToneSignal implements TapeSignal {

    private final Random random = new Random();

    private int nextTStateLevel = 2166;
    private long currentTStates = 0;
    private boolean currentLevel = false;

    @Override
    public boolean earLevelAt(long tstates) {
        if (tstates < currentTStates + nextTStateLevel) {
            return currentLevel;
        }
        currentTStates = tstates;
        currentLevel = !currentLevel;
        nextTStateLevel = random.nextInt(1000, 3000);
        return currentLevel;
    }

    @Override
    public void setSectionIndex(int index) {
        // Not applicable for silent signal
    }

    @Override
    public void setMotor(boolean on, long currentTStates) {
        this.currentTStates = currentTStates;
    }
}
