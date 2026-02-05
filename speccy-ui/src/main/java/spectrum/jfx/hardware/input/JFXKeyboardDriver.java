package spectrum.jfx.hardware.input;

import javafx.scene.input.KeyCode;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.helper.VolatileBoolMatrix;
import spectrum.hardware.input.KeyboardDriver;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bit 0: SHIFT, Z, X, C, V
 * Bit 1: A, S, D, F, G
 * Bit 2: Q, W, E, R, T
 * Bit 3: 1, 2, 3, 4, 5
 * Bit 4: 0, 9, 8, 7, 6
 * Bit 5: P, O, I, U, Y
 * Bit 6: ENTER, L, K, J, H
 * Bit 7: SPACE, SYMBOL, M, N, B
 */
@Slf4j
public class JFXKeyboardDriver implements KeyboardDriver {

    private record KeyPosition(int row, int col) {
    }

    private final Map<KeyCode, Set<KeyPosition>> keyMapping = new HashMap<>();
    private final VolatileBoolMatrix keyMatrix = new VolatileBoolMatrix(8, 5);

    public JFXKeyboardDriver() {
        initializeKeyMapping();
    }

    @Override
    public int readKeyboard(int addressLineMask) {
        int result = 0x1F; // All keys released by default

        // Check each row based on the port address
        for (int row = 0; row < 8; row++) {
            // If the row bit is set in the port address, check the columns
            if ((addressLineMask & (1 << row)) == 0) {
                for (int col = 0; col < 5; col++) {
                    if (keyMatrix.get(row, col)) {
                        // If a key is pressed in this row and column, set the corresponding bit in the result
                        result &= ~(1 << col);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void init() {
        // Nothing to do
    }

    @Override
    public void reset() {
        resetKeyboard();
    }

    @Override
    public void close() {
        // Nothing to do
    }

    public void resetKeyboard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 5; col++) {
                keyMatrix.set(row, col, false);
            }
        }
        log.debug("Keyboard state reset");
    }

    public void keyPressed(KeyCode keyCode) {
        Set<KeyPosition> pos = keyMapping.get(keyCode);
        if (pos != null) {
            pos.forEach(p -> keyMatrix.set(p.row, p.col, true));
            log.debug("Key pressed: {} at positions: {}", keyCode, pos);
        } else {
            log.debug("Unknown key pressed: {}", keyCode);
        }
    }

    public void keyReleased(KeyCode keyCode) {
        Set<KeyPosition> pos = keyMapping.get(keyCode);
        if (pos != null) {
            pos.forEach(p -> keyMatrix.set(p.row, p.col, false));
            log.debug("Key released: {}", keyCode);
        }
    }

    private void initializeKeyMapping() {
        // Row 0 (Bit 0): SHIFT, Z, X, C, V
        keyMapping.put(KeyCode.SHIFT, Set.of(new KeyPosition(0, 0)));
        keyMapping.put(KeyCode.Z, Set.of(new KeyPosition(0, 1)));
        keyMapping.put(KeyCode.X, Set.of(new KeyPosition(0, 2)));
        keyMapping.put(KeyCode.C, Set.of(new KeyPosition(0, 3)));
        keyMapping.put(KeyCode.V, Set.of(new KeyPosition(0, 4)));

        // Row 1 (Bit 1): A, S, D, F, G
        keyMapping.put(KeyCode.A, Set.of(new KeyPosition(1, 0)));
        keyMapping.put(KeyCode.S, Set.of(new KeyPosition(1, 1)));
        keyMapping.put(KeyCode.D, Set.of(new KeyPosition(1, 2)));
        keyMapping.put(KeyCode.F, Set.of(new KeyPosition(1, 3)));
        keyMapping.put(KeyCode.G, Set.of(new KeyPosition(1, 4)));

        // Row 2 (Bit 2): Q, W, E, R, T
        keyMapping.put(KeyCode.Q, Set.of(new KeyPosition(2, 0)));
        keyMapping.put(KeyCode.W, Set.of(new KeyPosition(2, 1)));
        keyMapping.put(KeyCode.E, Set.of(new KeyPosition(2, 2)));
        keyMapping.put(KeyCode.R, Set.of(new KeyPosition(2, 3)));
        keyMapping.put(KeyCode.T, Set.of(new KeyPosition(2, 4)));

        // Row 3 (Bit 3): 1, 2, 3, 4, 5
        keyMapping.put(KeyCode.DIGIT1, Set.of(new KeyPosition(3, 0)));
        keyMapping.put(KeyCode.DIGIT2, Set.of(new KeyPosition(3, 1)));
        keyMapping.put(KeyCode.DIGIT3, Set.of(new KeyPosition(3, 2)));
        keyMapping.put(KeyCode.DIGIT4, Set.of(new KeyPosition(3, 3)));
        keyMapping.put(KeyCode.DIGIT5, Set.of(new KeyPosition(3, 4)));

        // Row 4 (Bit 4): 0, 9, 8, 7, 6
        keyMapping.put(KeyCode.DIGIT0, Set.of(new KeyPosition(4, 0)));
        keyMapping.put(KeyCode.DIGIT9, Set.of(new KeyPosition(4, 1)));
        keyMapping.put(KeyCode.DIGIT8, Set.of(new KeyPosition(4, 2)));
        keyMapping.put(KeyCode.DIGIT7, Set.of(new KeyPosition(4, 3)));
        keyMapping.put(KeyCode.DIGIT6, Set.of(new KeyPosition(4, 4)));

        // Row 5 (Bit 5): P, O, I, U, Y
        keyMapping.put(KeyCode.P, Set.of(new KeyPosition(5, 0)));
        keyMapping.put(KeyCode.O, Set.of(new KeyPosition(5, 1)));
        keyMapping.put(KeyCode.I, Set.of(new KeyPosition(5, 2)));
        keyMapping.put(KeyCode.U, Set.of(new KeyPosition(5, 3)));
        keyMapping.put(KeyCode.Y, Set.of(new KeyPosition(5, 4)));

        // Row 6 (Bit 6): ENTER, L, K, J, H
        keyMapping.put(KeyCode.ENTER, Set.of(new KeyPosition(6, 0)));
        keyMapping.put(KeyCode.L, Set.of(new KeyPosition(6, 1)));
        keyMapping.put(KeyCode.K, Set.of(new KeyPosition(6, 2)));
        keyMapping.put(KeyCode.J, Set.of(new KeyPosition(6, 3)));
        keyMapping.put(KeyCode.H, Set.of(new KeyPosition(6, 4)));

        // Row 7 (Bit 7): SPACE, SYMBOL, M, N, B
        keyMapping.put(KeyCode.SPACE, Set.of(new KeyPosition(7, 0)));
        keyMapping.put(KeyCode.ALT, Set.of(new KeyPosition(7, 1)));     // SYMBOL SHIFT
        keyMapping.put(KeyCode.M, Set.of(new KeyPosition(7, 2)));
        keyMapping.put(KeyCode.N, Set.of(new KeyPosition(7, 3)));
        keyMapping.put(KeyCode.B, Set.of(new KeyPosition(7, 4)));

        // Дополнительные маппинги для удобства
        keyMapping.put(KeyCode.CONTROL, Set.of(new KeyPosition(7, 1))); // SYMBOL SHIFT как Ctrl
        keyMapping.put(KeyCode.LEFT, Set.of(new KeyPosition(0, 0), new KeyPosition(3, 4)));    // CAPS SHIFT + 5 (LEFT)
        keyMapping.put(KeyCode.DOWN, Set.of(new KeyPosition(0, 0), new KeyPosition(4, 4)));    // CAPS SHIFT + 6 (DOWN)
        keyMapping.put(KeyCode.UP, Set.of(new KeyPosition(0, 0), new KeyPosition(4, 3)));      // CAPS SHIFT + 7 (UP)
        keyMapping.put(KeyCode.RIGHT, Set.of(new KeyPosition(0, 0), new KeyPosition(4, 2)));   // CAPS SHIFT + 8 (RIGHT)

        keyMapping.put(KeyCode.DELETE, Set.of(new KeyPosition(0, 0), new KeyPosition(4, 0)));  // CAPS SHIFT + 0 (DELETE)
        keyMapping.put(KeyCode.BACK_SPACE, Set.of(new KeyPosition(0, 0), new KeyPosition(4, 0))); // CAPS SHIFT + 0 (DELETE)

        keyMapping.put(KeyCode.QUOTE, Set.of(new KeyPosition(7, 1), new KeyPosition(5, 0)));  // SYMBOL SHIFT + P (QUOTE)
        keyMapping.put(KeyCode.COMMA, Set.of(new KeyPosition(7, 1), new KeyPosition(7, 3)));  // SYMBOL SHIFT + N (COMMA)

        log.debug("Key mapping initialized with {} keys", keyMapping.size());
    }


}
