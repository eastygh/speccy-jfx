package spectrum.jfx.hardware.tape;

public class TapFilePlayback implements TapeSignal {

    private final boolean initialLevel;
    private long startTStates = 0;
    private boolean motorOn = false;

    public TapFilePlayback(boolean initialLevel, String fileName) {
        this.initialLevel = initialLevel;
    }

    @Override
    public void setMotor(boolean on, long currentTStates) {
        if (motorOn == on) return;
        motorOn = on;
        if (on) startTStates = currentTStates; // перезапуск «фазы»
    }

    @Override
    public boolean earLevelAt(long tstates) {
        return false;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

}
