package spectrum.hardware.input;

import spectrum.hardware.machine.Driver;

/**
 * Interface for a physical keyboard driver.
 */
public interface KeyboardDriver extends Driver {

    /**
     * Read keyboard state.
     *
     * @param lineMask mask of lines to read
     * @return keyboard state
     */
    int readKeyboard(int lineMask);

}
