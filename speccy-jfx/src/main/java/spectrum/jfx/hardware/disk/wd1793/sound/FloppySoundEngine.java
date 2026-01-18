package spectrum.jfx.hardware.disk.wd1793.sound;

import spectrum.jfx.hardware.disk.wd1793.ControllerState;
import spectrum.jfx.hardware.machine.Device;

public interface FloppySoundEngine extends Device {

    void ticks(long tStates, ControllerState state, boolean writeMode);

    void step(int count);

    default void step() {
        step(1);
    }

}
