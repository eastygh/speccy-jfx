package spectrum.jfx.hardware.tape;

public interface TapeSignal {

    boolean earLevelAt(long tstates);

    default boolean isFinished() {
        return false;
    }

}
