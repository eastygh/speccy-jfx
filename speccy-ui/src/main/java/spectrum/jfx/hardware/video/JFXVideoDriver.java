package spectrum.jfx.hardware.video;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import lombok.Getter;
import spectrum.hardware.video.VideoDriver;
import spectrum.hardware.video.ZoomLevel;

import java.nio.IntBuffer;

import static spectrum.jfx.hardware.video.SpectrumVideo.SPECTRUM_COLORS_ARGB;

public class JFXVideoDriver implements VideoDriver {

    @Getter
    private Canvas canvas;
    @Getter
    private GraphicsContext gc;
    private WritableImage screenImage;
    private PixelBuffer<IntBuffer> pixelBuffer;
    private int[] scaledPixels;
    @Getter
    private ZoomLevel currentZoom = ZoomLevel.X2;

    private boolean initialized = false;

    @Override
    public void init() {
        if (initialized) {
            return;
        }
        int scaledTotalWidth = getScaledTotalWidth();
        int scaledTotalHeight = getScaledTotalHeight();
        this.canvas = new Canvas(scaledTotalWidth, scaledTotalHeight);
        this.gc = canvas.getGraphicsContext2D();

        scaledPixels = new int[scaledTotalWidth * scaledTotalHeight];
        IntBuffer buffer = IntBuffer.wrap(scaledPixels);

        pixelBuffer = new PixelBuffer<>(scaledTotalWidth, scaledTotalHeight, buffer,
                PixelFormat.getIntArgbPreInstance());
        screenImage = new WritableImage(pixelBuffer);

        this.initialized = true;
    }

    @Override
    public void setZoomLevel(ZoomLevel zoomLevel) {
        currentZoom = zoomLevel;
    }

    @Override
    public void drawPixel(int x, int y, int color) {
        if (!initialized) {
            return;
        }
        setPixel(x, y, SPECTRUM_COLORS_ARGB[color]);
    }

    /**
     * Draw a pixel on the buffer in argb.
     *
     * @param x     - x coordinate
     * @param y     - y coordinate
     * @param iARGB - argb color
     */
    private void setPixel(int x, int y, int iARGB) {
        int scaledX = x * currentZoom.getScale();
        int scaledY = y * currentZoom.getScale();
        int scaledWidth = getScaledTotalWidth();
        for (int dy = 0; dy < currentZoom.getScale(); dy++) {
            for (int dx = 0; dx < currentZoom.getScale(); dx++) {
                int index = (scaledY + dy) * scaledWidth + (scaledX + dx);
                scaledPixels[index] = iARGB;
            }
        }
    }

    @Override
    public void refreshScreen() {
        if (!initialized) {
            return;
        }
        drawSnapshot();
    }

    private void drawSnapshot() {
        Platform.runLater(() -> {
            pixelBuffer.updateBuffer(pb -> null);
            gc.drawImage(screenImage, 0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

}
