package spectrum.hardware.ula;

public interface ClockListener {

    void ticks(long tStates, int delta);

    default void addressOnBus(int address, int tstates) {
        // do nothing
    }

}
