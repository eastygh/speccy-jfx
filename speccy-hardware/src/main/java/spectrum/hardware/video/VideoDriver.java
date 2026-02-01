package spectrum.hardware.video;

public interface VideoDriver {

    void init();

    void refreshScreen();

    /**
     * Draw a pixel on the physical screen.
     * @param x - X coordinate of the pixel
     * @param y - Y coordinate of the pixel
     * @param color - Spectrum Color of the pixel
     */
    void drawPixel(int x, int y, int color);

    default void setZoomLevel(ZoomLevel zoomLevel) {
        // do nothing
    }

    default ZoomLevel getCurrentZoom() {
        return ZoomLevel.X2;
    }

    default int getScaledScreenWidth() {
        return Video.SCREEN_WIDTH * getCurrentZoom().getScale();
    }

    default int getScaledScreenHeight() {
        return Video.SCREEN_HEIGHT * getCurrentZoom().getScale();
    }

    default int getScaledTotalWidth() {
        return Video.SCREEN_WIDTH * getCurrentZoom().getScale();
    }

    default int getScaledTotalHeight() {
        return Video.TOTAL_HEIGHT * getCurrentZoom().getScale();
    }

    default int getScaledBorderSize() {
        return Video.BORDER_SIZE * getCurrentZoom().getScale();
    }


}
