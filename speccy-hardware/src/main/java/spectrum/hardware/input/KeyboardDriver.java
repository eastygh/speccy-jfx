package spectrum.hardware.input;

import spectrum.hardware.machine.Driver;

/**
 * Interface for a physical keyboard driver.
 */
public interface KeyboardDriver extends Driver {

    int readKeyboard(int port);

}
