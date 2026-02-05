package spectrum.hardware.input;

import lombok.Getter;
import lombok.Setter;

/**
 * Keyboard matrix:
 * * Bit 0: SHIFT, Z, X, C, V
 * * Bit 1: A, S, D, F, G
 * * Bit 2: Q, W, E, R, T
 * * Bit 3: 1, 2, 3, 4, 5
 * * Bit 4: 0, 9, 8, 7, 6
 * * Bit 5: P, O, I, U, Y
 * * Bit 6: ENTER, L, K, J, H
 * * Bit 7: SPACE, SYMBOL, M, N, B
 */
public class KeyboardImpl implements Keyboard {

    private static final int PORT_MASK_LOW = 0x00FF;
    private static final int PORT_VALUE_LOW = 0x00FE;
    private static final int PORT_MASK_HIGH = 0xFF00;

    @Getter
    @Setter
    private KeyboardDriver keyboardDriver;

    @Override
    public void init() {
        if (keyboardDriver != null) {
            keyboardDriver.init();
        }
    }

    @Override
    public void reset() {
        if (keyboardDriver != null) {
            keyboardDriver.reset();
        }
    }

    @Override
    public void open() {
        // do nothing
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public int inPort(int port) {
        if (keyboardDriver == null || !isKeyboardPort(port)) {
            // No keyboard driver or not a keyboard port
            return 0xFF;
        }
        int lineMask = (port & 0xFF00) >> 8;
        return keyboardDriver.readKeyboard(lineMask) & 0xFF;
    }

    private boolean isKeyboardPort(int port) {
        // Check low byte is 0xFE and high byte has at least one zero bit
        return (port & PORT_MASK_LOW) == PORT_VALUE_LOW &&
                ((port & PORT_MASK_HIGH) >> 8) != 0xFF;
    }

}
