package spectrum.jfx.hardware.machine;

public interface Device {

    void init();

    void reset();

    void open();

    void close();

    default void setSpeedUpMode(boolean speedUpMode) {

    }

}
