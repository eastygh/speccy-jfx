package spectrum.jfx.hardware.sound;

import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.OutPortListener;

public interface Sound extends OutPortListener, ClockListener {

    double getVolume();

    void setVolume(double volume);

    void start();

    void stop();

    void pushBackTape(boolean state);

}
