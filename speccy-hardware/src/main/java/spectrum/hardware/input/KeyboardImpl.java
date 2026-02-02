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
        return 0;
    }

}
