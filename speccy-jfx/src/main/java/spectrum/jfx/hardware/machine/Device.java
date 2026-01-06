package spectrum.jfx.hardware.machine;

import spectrum.jfx.snapshot.SnapShot;

public interface Device {

    void init();

    void reset();

    void open();

    void close();

    default void setSpeedUpMode(boolean speedUpMode) {

    }

    default SnapShot getSnapShot() {
        throw new UnsupportedOperationException("Device does not support snapshots");
    }

}
