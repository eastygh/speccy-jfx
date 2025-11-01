package spectrum.jfx.z80.video;

public interface Video<T> {

    // Разрешение экрана ZX Spectrum (оригинальное)
    int SCREEN_WIDTH = 256;
    int SCREEN_HEIGHT = 192;
    int BORDER_SIZE = 48; // Размер рамки
    int TOTAL_WIDTH = SCREEN_WIDTH + BORDER_SIZE * 2;
    int TOTAL_HEIGHT = SCREEN_HEIGHT + BORDER_SIZE * 2;

    void update(int cycles);

    void render();

    void markScreenDirty();

    void setZoomLevel(ZoomLevel newZoom);

    void nextZoomLevel();

    void previousZoomLevel();

    default ZoomLevel[] getAvailableZoomLevels() {
        return ZoomLevel.values();
    }

    T getCanvas();

    ZoomLevel getCurrentZoom();

    /**
     * ================
     * SCREEN SIZES
     * ================
     */
    default int getTotalWidth() {
        return TOTAL_WIDTH;
    }

    default int getTotalHeight() {
        return TOTAL_HEIGHT;
    }

    // Оригинальные размеры (без масштабирования)
    default int getScreenWidth() {
        return SCREEN_WIDTH;
    }

    default int getScreenHeight() {
        return SCREEN_HEIGHT;
    }

    default int getBorderSize() {
        return BORDER_SIZE;
    }

    // Масштабированные размеры (текущие размеры canvas)
    default int getScaledScreenWidth() {
        return getScreenWidth() * getCurrentZoom().getScale();
    }

    default int getScaledScreenHeight() {
        return getScreenHeight() * getCurrentZoom().getScale();
    }

    default int getScaledTotalWidth() {
        return getScreenWidth() * getCurrentZoom().getScale();
    }

    default int getScaledTotalHeight() {
        return getTotalHeight() * getCurrentZoom().getScale();
    }

    default int getScaledBorderSize() {
        return getBorderSize() * getCurrentZoom().getScale();
    }

}
