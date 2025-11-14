package spectrum.jfx.hardware.ula;

import spectrum.jfx.z80core.MemIoOps;

public interface Ula extends MemIoOps {

    void addPortListener(int port, InPortListener listener);

    void addPortListener(int port, OutPortListener listener);

    void requestInterrupt();

    void addTStates(int tStates);

    default void removePortListener(byte port, InPortListener listener) {
        throw new UnsupportedOperationException();
    }

}
