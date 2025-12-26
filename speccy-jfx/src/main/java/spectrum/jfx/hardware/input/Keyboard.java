package spectrum.jfx.hardware.input;

import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.helper.VolatileBoolMatrix;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Эмуляция клавиатуры ZX Spectrum
 * <p>
 * ZX Spectrum использует матричную клавиатуру 8x5 (8 полурядов по 5 клавиш)
 * Клавиатура считывается через порт 0xFE
 * <p>
 * Матрица клавиатуры:
 * Bit 0: SHIFT, Z, X, C, V
 * Bit 1: A, S, D, F, G
 * Bit 2: Q, W, E, R, T
 * Bit 3: 1, 2, 3, 4, 5
 * Bit 4: 0, 9, 8, 7, 6
 * Bit 5: P, O, I, U, Y
 * Bit 6: ENTER, L, K, J, H
 * Bit 7: SPACE, SYMBOL, M, N, B
 */
public class Keyboard implements InPortListener {

    private static final Logger logger = LoggerFactory.getLogger(Keyboard.class);

    // Состояние клавиатурной матрицы (8 байт)
    private final VolatileBoolMatrix keyMatrix = new VolatileBoolMatrix(8, 5);

    // Маппинг JavaFX KeyCode на позиции в матрице
    private final Map<KeyCode, Set<KeyPosition>> keyMapping = new HashMap<>();

    // Внутренний класс для представления позиции клавиши в матрице
    private record KeyPosition(int row, int col) {
    }

    public Keyboard() {
        logger.info("Initializing ZX Spectrum keyboard");
        initializeKeyMapping();
        resetKeyboard();
        logger.info("Keyboard initialized");
    }

    /**
     * Инициализирует маппинг клавиш
     */
    private void initializeKeyMapping() {
        // Полуряд 0 (Bit 0): SHIFT, Z, X, C, V
        keyMapping.put(KeyCode.SHIFT, Set.of(new KeyPosition(0, 0)));
        keyMapping.put(KeyCode.Z, Set.of(new KeyPosition(0, 1)));
        keyMapping.put(KeyCode.X, Set.of(new KeyPosition(0, 2)));
        keyMapping.put(KeyCode.C, Set.of(new KeyPosition(0, 3)));
        keyMapping.put(KeyCode.V, Set.of(new KeyPosition(0, 4)));

        // Полуряд 1 (Bit 1): A, S, D, F, G
        keyMapping.put(KeyCode.A, Set.of(new KeyPosition(1, 0)));
        keyMapping.put(KeyCode.S, Set.of(new KeyPosition(1, 1)));
        keyMapping.put(KeyCode.D, Set.of(new KeyPosition(1, 2)));
        keyMapping.put(KeyCode.F, Set.of(new KeyPosition(1, 3)));
        keyMapping.put(KeyCode.G, Set.of(new KeyPosition(1, 4)));

        // Полуряд 2 (Bit 2): Q, W, E, R, T
        keyMapping.put(KeyCode.Q, Set.of(new KeyPosition(2, 0)));
        keyMapping.put(KeyCode.W, Set.of(new KeyPosition(2, 1)));
        keyMapping.put(KeyCode.E, Set.of(new KeyPosition(2, 2)));
        keyMapping.put(KeyCode.R, Set.of(new KeyPosition(2, 3)));
        keyMapping.put(KeyCode.T, Set.of(new KeyPosition(2, 4)));

        // Полуряд 3 (Bit 3): 1, 2, 3, 4, 5
        keyMapping.put(KeyCode.DIGIT1, Set.of(new KeyPosition(3, 0)));
        keyMapping.put(KeyCode.DIGIT2, Set.of(new KeyPosition(3, 1)));
        keyMapping.put(KeyCode.DIGIT3, Set.of(new KeyPosition(3, 2)));
        keyMapping.put(KeyCode.DIGIT4, Set.of(new KeyPosition(3, 3)));
        keyMapping.put(KeyCode.DIGIT5, Set.of(new KeyPosition(3, 4)));

        // Полуряд 4 (Bit 4): 0, 9, 8, 7, 6
        keyMapping.put(KeyCode.DIGIT0, Set.of(new KeyPosition(4, 0)));
        keyMapping.put(KeyCode.DIGIT9, Set.of(new KeyPosition(4, 1)));
        keyMapping.put(KeyCode.DIGIT8, Set.of(new KeyPosition(4, 2)));
        keyMapping.put(KeyCode.DIGIT7, Set.of(new KeyPosition(4, 3)));
        keyMapping.put(KeyCode.DIGIT6, Set.of(new KeyPosition(4, 4)));

        // Полуряд 5 (Bit 5): P, O, I, U, Y
        keyMapping.put(KeyCode.P, Set.of(new KeyPosition(5, 0)));
        keyMapping.put(KeyCode.O, Set.of(new KeyPosition(5, 1)));
        keyMapping.put(KeyCode.I, Set.of(new KeyPosition(5, 2)));
        keyMapping.put(KeyCode.U, Set.of(new KeyPosition(5, 3)));
        keyMapping.put(KeyCode.Y, Set.of(new KeyPosition(5, 4)));

        // Полуряд 6 (Bit 6): ENTER, L, K, J, H
        keyMapping.put(KeyCode.ENTER, Set.of(new KeyPosition(6, 0)));
        keyMapping.put(KeyCode.L, Set.of(new KeyPosition(6, 1)));
        keyMapping.put(KeyCode.K, Set.of(new KeyPosition(6, 2)));
        keyMapping.put(KeyCode.J, Set.of(new KeyPosition(6, 3)));
        keyMapping.put(KeyCode.H, Set.of(new KeyPosition(6, 4)));

        // Полуряд 7 (Bit 7): SPACE, SYMBOL, M, N, B
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

        logger.debug("Key mapping initialized with {} keys", keyMapping.size());
    }

    /**
     * Сбрасывает состояние клавиатуры
     */
    public void resetKeyboard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 5; col++) {
                keyMatrix.set(row, col, false);
            }
        }
        logger.debug("Keyboard state reset");
    }

    /**
     * Обрабатывает нажатие клавиши
     */
    public void keyPressed(KeyCode keyCode) {
        Set<KeyPosition> pos = keyMapping.get(keyCode);
        if (pos != null) {
            pos.forEach(p -> keyMatrix.set(p.row, p.col, true));
            logger.debug("Key pressed: {} at positions: {}", keyCode, pos);
        } else {
            logger.debug("Unknown key pressed: {}", keyCode);
        }
    }

    /**
     * Обрабатывает отпускание клавиши
     */
    public void keyReleased(KeyCode keyCode) {
        Set<KeyPosition> pos = keyMapping.get(keyCode);
        if (pos != null) {
            pos.forEach(p -> keyMatrix.set(p.row, p.col, false));
            logger.debug("Key released: {}", keyCode);
        }
    }

    /**
     * Читает состояние клавиатуры через порт 0xFE
     *
     * @param addressLineMask Маска адресных линий A8-A15
     * @return Состояние клавиш (0 = нажата, 1 = не нажата)
     */
    public int readKeyboard(int addressLineMask) {
        int result = 0x1F; // Все клавиши отпущены по умолчанию

        // Проверяем каждый полуряд
        for (int row = 0; row < 8; row++) {
            // Если соответствующий бит в маске адреса равен 0, проверяем этот полуряд
            if ((addressLineMask & (1 << row)) == 0) {
                for (int col = 0; col < 5; col++) {
                    if (keyMatrix.get(row, col)) {
                        // Клавиша нажата, сбрасываем соответствующий бит
                        result &= ~(1 << col);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Проверяет, нажата ли конкретная клавиша
     */
    public boolean isKeyPressed(KeyCode keyCode) {
        return false;
    }

    /**
     * Имитирует нажатие клавиши на определенное время
     */
    public void simulateKeyPress(KeyCode keyCode, long durationMs) {
        keyPressed(keyCode);

        // Создаем отдельный поток для отпускания клавиши
        new Thread(() -> {
            try {
                Thread.sleep(durationMs);
                keyReleased(keyCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Получает текстовое представление нажатых клавиш (для отладки)
     */
    public String getPressedKeysString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pressed keys: ");

        boolean anyPressed = false;
//        for (Map.Entry<KeyCode, KeyPosition> entry : keyMapping.entrySet()) {
//            KeyPosition pos = entry.getValue();
//            if (keyMatrix.get(pos.row, pos.col)) {
//                if (anyPressed) {
//                    sb.append(", ");
//                }
//                sb.append(entry.getKey());
//                anyPressed = true;
//            }
//        }

        if (!anyPressed) {
            sb.append("none");
        }

        return sb.toString();
    }

    /**
     * Получает состояние клавиатурной матрицы (для отладки)
     */
    public boolean[][] getKeyMatrix() {
        // Возвращаем копию матрицы
//        boolean[][] copy = new boolean[8][5];
//        for (int row = 0; row < 8; row++) {
//            System.arraycopy(keyMatrix[row], 0, copy[row], 0, 5);
//        }
        return null;
    }

    /**
     * Выводит состояние клавиатуры в консоль (для отладки)
     */
    public void debugPrintMatrix() {
        logger.debug("Keyboard matrix state:");
        for (int row = 0; row < 8; row++) {
            StringBuilder sb = new StringBuilder();
            sb.append("Row ").append(row).append(": ");
            for (int col = 0; col < 5; col++) {
                sb.append(keyMatrix.get(row, col) ? "1 " : "0 ");
            }
            logger.debug(sb.toString());
        }
    }

    // Константы для специальных клавиш
    public static final KeyCode CAPS_SHIFT = KeyCode.SHIFT;
    public static final KeyCode SYMBOL_SHIFT = KeyCode.ALT;

    /**
     * Проверяет, нажата ли CAPS SHIFT
     */
    public boolean isCapsShiftPressed() {
        return isKeyPressed(CAPS_SHIFT);
    }

    /**
     * Проверяет, нажата ли SYMBOL SHIFT
     */
    public boolean isSymbolShiftPressed() {
        return isKeyPressed(SYMBOL_SHIFT);
    }

    @Override
    public int inPort(int port) {
        int lineMask = (port & 0xFF00) >> 8;
        return readKeyboard(lineMask) & 0xFF;
    }

}