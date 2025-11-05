package spectrum.jfx.z80.ula;

import spectrum.jfx.z80core.MemIoOps;

public interface Ula extends MemIoOps {

    void addPortListener(byte port, InPortListener listener);

    void requestInterrupt();

    default void removePortListener(byte port, InPortListener listener) {
        throw new UnsupportedOperationException();
    }

}
