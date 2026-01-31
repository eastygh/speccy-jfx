package spectrum.hardware.machine;

import spectrum.hardware.snapshot.SnapShot;

public interface Device {

    void init();

    void reset();

    void open();

    void close();

    default void setSpeedUpMode(boolean speedUpMode) {
        // ignore
    }

    default SnapShot getSnapShot() {
        throw new UnsupportedOperationException("Device does not support snapshots");
    }

}
