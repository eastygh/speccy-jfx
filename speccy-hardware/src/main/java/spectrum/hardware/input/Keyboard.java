package spectrum.hardware.input;

import spectrum.hardware.machine.Device;
import spectrum.hardware.ula.InPortListener;

public interface Keyboard extends Device, InPortListener {

    void setKeyboardDriver(KeyboardDriver keyboardDriver);

    KeyboardDriver getKeyboardDriver();

}
