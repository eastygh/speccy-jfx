package spectrum.jfx.hardware.ula;

public interface Ula extends z80core.MemIoOps {

    void addPortListener(int port, InPortListener listener);

    void addPortListener(int port, OutPortListener listener);

    void addClockListener(ClockListener listener);

    void requestInterrupt();

    void addTStates(int tStates);

    default void removePortListener(byte port, InPortListener listener) {
        throw new UnsupportedOperationException();
    }

}
