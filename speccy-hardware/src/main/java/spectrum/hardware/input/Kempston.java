package spectrum.hardware.input;

import spectrum.hardware.machine.Device;
import spectrum.hardware.ula.InPortListener;

public interface Kempston extends InPortListener, Device {

    void init();

    GamePad getGamePad();

}
