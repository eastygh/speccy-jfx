package spectrum.jfx.hardware.tape;

public interface TapeSignal {

    boolean earLevelAt(long tstates);

    void setMotor(boolean on, long currentTStates);

    default boolean isStarted() {
        return false;
    }

    default boolean isFinished() {
        return false;
    }

}
