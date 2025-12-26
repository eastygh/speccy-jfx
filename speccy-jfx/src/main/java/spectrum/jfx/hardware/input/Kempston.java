package spectrum.jfx.hardware.input;

import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.ula.InPortListener;

public interface Kempston extends InPortListener, Device {

    void init();

    GamePad getGamePad();

}
