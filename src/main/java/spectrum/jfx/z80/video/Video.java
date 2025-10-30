package spectrum.jfx.z80.video;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.z80.memory.Memory;

/**
 * Эмуляция видеосистемы ZX Spectrum
 * Управляет отображением экрана с разрешением 256x192 пикселя
 * <p>
 * Карта видеопамяти ZX Spectrum:
 * 0x4000-0x57FF: Bitmap data (6144 bytes)
 * 0x5800-0x5AFF: Attribute data (768 bytes)
 */
public class Video {
    private static final Logger logger = LoggerFactory.getLogger(Video.class);

    /**
     * Уровни масштабирования экрана
     */
    public enum ZoomLevel {
        X1(1, "1x"),
        X2(2, "2x"),
        X4(4, "4x");

        private final int scale;
        private final String displayName;

        ZoomLevel(int scale, String displayName) {
            this.scale = scale;
            this.displayName = displayName;
        }

        public int getScale() {
            return scale;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Разрешение экрана ZX Spectrum (оригинальное)
    public static final int SCREEN_WIDTH = 256;
    public static final int SCREEN_HEIGHT = 192;
    public static final int BORDER_SIZE = 48; // Размер рамки
    public static final int TOTAL_WIDTH = SCREEN_WIDTH + BORDER_SIZE * 2;
    public static final int TOTAL_HEIGHT = SCREEN_HEIGHT + BORDER_SIZE * 2;

    // Адреса видеопамяти
    private static final int BITMAP_START = 0x4000;
    private static final int BITMAP_SIZE = 6144;   // 256*192/8
    private static final int ATTR_START = 0x5800;
    private static final int ATTR_SIZE = 768;      // 32*24

    // Стандартная палитра ZX Spectrum
    private static final Color[] SPECTRUM_COLORS = {
            Color.rgb(0, 0, 0),       // 0: Black
            Color.rgb(0, 0, 215),     // 1: Blue
            Color.rgb(215, 0, 0),     // 2: Red
            Color.rgb(215, 0, 215),   // 3: Magenta
            Color.rgb(0, 215, 0),     // 4: Green
            Color.rgb(0, 215, 215),   // 5: Cyan
            Color.rgb(215, 215, 0),   // 6: Yellow
            Color.rgb(215, 215, 215), // 7: White

            // Bright colors (с флагом BRIGHT)
            Color.rgb(0, 0, 0),       // 8: Bright Black
            Color.rgb(0, 0, 255),     // 9: Bright Blue
            Color.rgb(255, 0, 0),     // 10: Bright Red
            Color.rgb(255, 0, 255),   // 11: Bright Magenta
            Color.rgb(0, 255, 0),     // 12: Bright Green
            Color.rgb(0, 255, 255),   // 13: Bright Cyan
            Color.rgb(255, 255, 0),   // 14: Bright Yellow
            Color.rgb(255, 255, 255)  // 15: Bright White
    };

    private final Memory memory;
    private Canvas canvas;
    private GraphicsContext gc;

    // Состояние видеосистемы
    private ZoomLevel currentZoom = ZoomLevel.X2; // По умолчанию x2 для удобства
    private Color borderColor = SPECTRUM_COLORS[7]; // По умолчанию белая рамка
    private boolean flashPhase = false; // Фаза мигания
    private long flashCounter = 0;

    // Буферы для оптимизации
    private final int[] pixelBuffer;
    private boolean screenDirty = true;

    public Video(Memory memory) {
        this(memory, ZoomLevel.X2); // По умолчанию x2 масштаб для удобства
    }

    public Video(Memory memory, ZoomLevel initialZoom) {
        this.memory = memory;
        this.currentZoom = initialZoom;

        // Создаем canvas с учетом масштаба
        int scaledWidth = TOTAL_WIDTH * currentZoom.getScale();
        int scaledHeight = TOTAL_HEIGHT * currentZoom.getScale();
        this.canvas = new Canvas(scaledWidth, scaledHeight);
        this.gc = canvas.getGraphicsContext2D();
        this.pixelBuffer = new int[TOTAL_WIDTH * TOTAL_HEIGHT]; // Буфер всегда оригинального размера

        logger.info("Video system initialized: {}x{} (scaled: {}x{}, zoom: {})",
                SCREEN_WIDTH, SCREEN_HEIGHT, scaledWidth, scaledHeight, currentZoom.getDisplayName());

        initializeScreen();
    }

    /**
     * Инициализирует экран
     */
    private void initializeScreen() {
        int scale = currentZoom.getScale();
        int scaledWidth = TOTAL_WIDTH * scale;
        int scaledHeight = TOTAL_HEIGHT * scale;

        // Очистка экрана черным цветом
        gc.setFill(SPECTRUM_COLORS[0]);
        gc.fillRect(0, 0, scaledWidth, scaledHeight);

        // Рисуем рамку
        drawBorder();

        logger.debug("Screen initialized with zoom {}", currentZoom.getDisplayName());
    }

    /**
     * Обновляет видеосистему
     */
    public void update(int cycles) {
        // Обновляем счетчик мигания (flash)
        flashCounter += cycles;
        if (flashCounter >= 875000) { // Примерно 16 раз в секунду при 3.5MHz
            flashPhase = !flashPhase;
            flashCounter = 0;
            screenDirty = true; // Нужно перерисовать экран из-за мигания
        }

        // Помечаем экран как требующий обновления при записи в видеопамять
        // (это будет вызываться из Memory класса)
    }

    /**
     * Рендеринг кадра
     */
    public void render() {
        if (!screenDirty) {
            return; // Экран не изменился
        }

        drawBorder();
        drawScreen();

        screenDirty = false;
    }

    /**
     * Рисует рамку экрана
     */
    private void drawBorder() {
        int scale = currentZoom.getScale();
        int scaledBorderSize = BORDER_SIZE * scale;
        int scaledScreenWidth = SCREEN_WIDTH * scale;
        int scaledScreenHeight = SCREEN_HEIGHT * scale;
        int scaledTotalWidth = TOTAL_WIDTH * scale;

        gc.setFill(borderColor);

        // Верхняя рамка
        gc.fillRect(0, 0, scaledTotalWidth, scaledBorderSize);

        // Нижняя рамка
        gc.fillRect(0, scaledBorderSize + scaledScreenHeight, scaledTotalWidth, scaledBorderSize);

        // Левая рамка
        gc.fillRect(0, scaledBorderSize, scaledBorderSize, scaledScreenHeight);

        // Правая рамка
        gc.fillRect(scaledBorderSize + scaledScreenWidth, scaledBorderSize, scaledBorderSize, scaledScreenHeight);
    }

    /**
     * Рисует основную область экрана
     */
    private void drawScreen() {
        for (int y = 0; y < SCREEN_HEIGHT; y++) {
            for (int x = 0; x < SCREEN_WIDTH; x++) {
                drawPixel(x, y);
            }
        }
    }

    /**
     * Рисует один пиксель на экране
     */
    private void drawPixel(int x, int y) {
        // Получаем байт bitmap данных
        int bitmapAddr = getBitmapAddress(x, y);
        int bitmapByte = memory.readByte(bitmapAddr);

        // Получаем атрибуты цвета
        int attrAddr = getAttributeAddress(x, y);
        int attrByte = memory.readByte(attrAddr);

        // Декодируем атрибуты
        int ink = attrByte & 0x07;           // Цвет чернил (0-7)
        int paper = (attrByte >> 3) & 0x07;  // Цвет бумаги (0-7)
        boolean bright = (attrByte & 0x40) != 0;  // Флаг BRIGHT
        boolean flash = (attrByte & 0x80) != 0;   // Флаг FLASH

        // Если bright установлен, добавляем 8 к индексу цвета
        if (bright) {
            ink += 8;
            paper += 8;
        }

        // Определяем, какой пиксель в байте
        int bitPosition = 7 - (x % 8);
        boolean pixelSet = (bitmapByte & (1 << bitPosition)) != 0;

        // Учитываем мигание
        if (flash && flashPhase) {
            pixelSet = !pixelSet; // Инвертируем пиксель
        }

        // Выбираем цвет
        Color color = pixelSet ? SPECTRUM_COLORS[ink] : SPECTRUM_COLORS[paper];

        // Рисуем масштабированный пиксель на canvas
        int scale = currentZoom.getScale();
        int scaledBorderSize = BORDER_SIZE * scale;
        int scaledX = scaledBorderSize + (x * scale);
        int scaledY = scaledBorderSize + (y * scale);

        gc.setFill(color);
        gc.fillRect(scaledX, scaledY, scale, scale);
    }

    /**
     * Вычисляет адрес bitmap данных для пикселя
     * ZX Spectrum использует специальную схему адресации
     */
    private int getBitmapAddress(int x, int y) {
        int line = y & 0x07;          // Младшие 3 бита Y
        int third = (y >> 3) & 0x07;  // Средние 3 бита Y
        int section = (y >> 6) & 0x03; // Старшие 2 бита Y
        int column = x >> 3;           // X / 8

        return BITMAP_START + (section << 11) + (line << 8) + (third << 5) + column;
    }

    /**
     * Вычисляет адрес атрибутов для пикселя
     */
    private int getAttributeAddress(int x, int y) {
        int row = y >> 3;    // Y / 8
        int col = x >> 3;    // X / 8
        return ATTR_START + (row << 5) + col;
    }

    /**
     * Устанавливает цвет рамки
     */
    public void setBorderColor(int colorIndex) {
        if (colorIndex >= 0 && colorIndex < 8) {
            this.borderColor = SPECTRUM_COLORS[colorIndex];
            screenDirty = true;
            logger.debug("Border color set to: {}", colorIndex);
        }
    }

    /**
     * Отмечает экран как требующий обновления
     * Вызывается при записи в видеопамять
     */
    public void markScreenDirty() {
        screenDirty = true;
    }

    /**
     * Проверяет, находится ли адрес в видеопамяти
     */
    public boolean isVideoMemory(int address) {
        return (address >= BITMAP_START && address < BITMAP_START + BITMAP_SIZE) ||
                (address >= ATTR_START && address < ATTR_START + ATTR_SIZE);
    }

    /**
     * Создает скриншот экрана в формате ZX Spectrum
     */
    public byte[] takeScreenshot() {
        byte[] screenshot = new byte[BITMAP_SIZE + ATTR_SIZE];

        // Копируем bitmap данные
        for (int i = 0; i < BITMAP_SIZE; i++) {
            screenshot[i] = (byte) memory.readByte(BITMAP_START + i);
        }

        // Копируем атрибуты
        for (int i = 0; i < ATTR_SIZE; i++) {
            screenshot[BITMAP_SIZE + i] = (byte) memory.readByte(ATTR_START + i);
        }

        return screenshot;
    }

    /**
     * Загружает скриншот в видеопамять
     */
    public void loadScreenshot(byte[] screenshot) {
        if (screenshot.length != BITMAP_SIZE + ATTR_SIZE) {
            throw new IllegalArgumentException("Invalid screenshot size");
        }

        // Загружаем bitmap данные
        for (int i = 0; i < BITMAP_SIZE; i++) {
            memory.writeByte(BITMAP_START + i, screenshot[i] & 0xFF);
        }

        // Загружаем атрибуты
        for (int i = 0; i < ATTR_SIZE; i++) {
            memory.writeByte(ATTR_START + i, screenshot[BITMAP_SIZE + i] & 0xFF);
        }

        screenDirty = true;
        logger.info("Screenshot loaded into video memory");
    }

    /**
     * Изменяет масштаб отображения
     */
    public void setZoomLevel(ZoomLevel newZoom) {
        if (newZoom == currentZoom) {
            return; // Масштаб не изменился
        }

        ZoomLevel oldZoom = currentZoom;
        currentZoom = newZoom;

        // Пересоздаем canvas с новым размером
        int scaledWidth = TOTAL_WIDTH * currentZoom.getScale();
        int scaledHeight = TOTAL_HEIGHT * currentZoom.getScale();

        // Создаем новый canvas
        canvas = new Canvas(scaledWidth, scaledHeight);
        gc = canvas.getGraphicsContext2D();

        // Принудительно перерисовываем экран
        screenDirty = true;
        initializeScreen();

        logger.info("Zoom level changed from {} to {} (canvas size: {}x{})",
                oldZoom.getDisplayName(), currentZoom.getDisplayName(), scaledWidth, scaledHeight);
    }

    /**
     * Переключает на следующий уровень масштабирования
     */
    public void nextZoomLevel() {
        ZoomLevel[] levels = ZoomLevel.values();
        int currentIndex = currentZoom.ordinal();
        int nextIndex = (currentIndex + 1) % levels.length;
        setZoomLevel(levels[nextIndex]);
    }

    /**
     * Переключает на предыдущий уровень масштабирования
     */
    public void previousZoomLevel() {
        ZoomLevel[] levels = ZoomLevel.values();
        int currentIndex = currentZoom.ordinal();
        int prevIndex = (currentIndex - 1 + levels.length) % levels.length;
        setZoomLevel(levels[prevIndex]);
    }

    // Геттеры

    public Canvas getCanvas() {
        return canvas;
    }

    // Оригинальные размеры (без масштабирования)
    public int getScreenWidth() {
        return SCREEN_WIDTH;
    }

    public int getScreenHeight() {
        return SCREEN_HEIGHT;
    }

    public int getTotalWidth() {
        return TOTAL_WIDTH;
    }

    public int getTotalHeight() {
        return TOTAL_HEIGHT;
    }

    // Масштабированные размеры (текущие размеры canvas)
    public int getScaledScreenWidth() {
        return SCREEN_WIDTH * currentZoom.getScale();
    }

    public int getScaledScreenHeight() {
        return SCREEN_HEIGHT * currentZoom.getScale();
    }

    public int getScaledTotalWidth() {
        return TOTAL_WIDTH * currentZoom.getScale();
    }

    public int getScaledTotalHeight() {
        return TOTAL_HEIGHT * currentZoom.getScale();
    }

    public int getScaledBorderSize() {
        return BORDER_SIZE * currentZoom.getScale();
    }

    // Информация о масштабировании
    public ZoomLevel getCurrentZoom() {
        return currentZoom;
    }

    public int getCurrentScale() {
        return currentZoom.getScale();
    }

    public String getZoomDisplayName() {
        return currentZoom.getDisplayName();
    }

    public ZoomLevel[] getAvailableZoomLevels() {
        return ZoomLevel.values();
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public boolean isFlashPhase() {
        return flashPhase;
    }

    public static Color[] getSpectrumColors() {
        return SPECTRUM_COLORS.clone();
    }
}