package spectrum.hardware.ula;

public interface ClockListener {

    // Clock ticks
    void ticks(long tStates, int delta);

    default void addressOnBus(int address, int tstates) {
        // do nothing
    }

    // End of frame processing
    default void endFrame() {
        // do nothing
    }

}
