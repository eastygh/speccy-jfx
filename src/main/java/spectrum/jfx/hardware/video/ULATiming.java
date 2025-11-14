package spectrum.jfx.hardware.video;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ULATiming {

    // Тайминги для 48K Spectrum (PAL)
    static final int TSTATES_PER_LINE = 224;     // Тактов на строку
    static final int SCREEN_LINES = 192;         // Видимые строки экрана
    static final int BORDER_TOP_LINES = 64;      // Верхний бордюр
    static final int BORDER_BOTTOM_LINES = 56;   // Нижний бордюр
    static final int TOTAL_LINES = 312;          // Всего строк в кадре

    static final int LEFT_BORDER_TSTATES = 24;   // Левый бордюр
    static final int SCREEN_TSTATES = 128;       // Основной экран (256 пикселей)
    static final int RIGHT_BORDER_TSTATES = 72;  // Правый бордюр + обратный ход

    static final int TSTATES_PER_FRAME = 69888;  // Тактов на кадр (50Hz)

}
