package spectrum.jfx.hardware.ula;

import spectrum.jfx.z80core.MemIoOps;

public interface Ula extends MemIoOps {

    void addPortListener(byte port, InPortListener listener);

    void addPortListener(byte port, OutPortListener listener);

    void requestInterrupt();

    default void removePortListener(byte port, InPortListener listener) {
        throw new UnsupportedOperationException();
    }

}
