package spectrum.jfx.hardware.video;

import spectrum.hardware.machine.Device;
import spectrum.hardware.video.ZoomLevel;

/**
 * Video interface for Spectrum hardware.
 * T is an implementation of VideoDriver.
 */
public interface VideoLegacy extends Device {

    int SCREEN_WIDTH = 256;
    int SCREEN_HEIGHT = 192;
    int BORDER_SIZE = 48;
    int BORDER_V_SIZE = 48; // Vertical border size
    int BORDER_H_SIZE = 48; // Horizontal border size
    int TOTAL_WIDTH = SCREEN_WIDTH + BORDER_H_SIZE * 2;
    int TOTAL_HEIGHT = SCREEN_HEIGHT + BORDER_V_SIZE * 2;

    void update(int cycles);

    void render();

    void markScreenDirty();

    void setZoomLevel(ZoomLevel newZoom);

    void nextZoomLevel();

    void previousZoomLevel();

    default ZoomLevel[] getAvailableZoomLevels() {
        return ZoomLevel.values();
    }

    ZoomLevel getCurrentZoom();

    default void tStatesTicks(int tStates) {
        // ignore
    }

    default void reset() {
        // ignore
    }

    default void start() {
        // ignore
    }

    default void stop() {
        // ignore
    }

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
