package spectrum.jfx.hardware.video;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.OutPortListener;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static spectrum.jfx.hardware.video.SpectrumVideo.*;

public class ScanlineVideoImpl implements Video<Canvas>, OutPortListener, ClockListener {

    private final Memory memory;
    @Getter
    private final Canvas canvas;
    private final GraphicsContext gc;
    private ZoomLevel currentZoom = ZoomLevel.X2;

    private WritableImage screenImage;
    private PixelBuffer<IntBuffer> pixelBuffer;
    private IntBuffer buffer;
    private int[] scaledPixels;

    private final AtomicReference<AddressOnBusEvent> addressOnBus = new AtomicReference<>();

    private volatile long currentTState = 0;
    private volatile int currentLineTState = 0;
    private volatile int frameCounter = 0;

    private volatile int borderColor = 0;
    private volatile int currentX = 0;
    private volatile int currentY = 0;
    private volatile boolean dirtyScreen = false;


    private final int[] pixels = new int[TOTAL_HEIGHT * TOTAL_WIDTH];

    Thread renderThread;

    public ScanlineVideoImpl(Memory memory) {
        this.memory = memory;

        int scaledWidth = TOTAL_WIDTH * currentZoom.getScale();
        int scaledHeight = TOTAL_HEIGHT * currentZoom.getScale();

        this.canvas = new Canvas(scaledWidth, scaledHeight);
        this.gc = canvas.getGraphicsContext2D();

        scaledPixels = new int[scaledWidth * scaledHeight];
        buffer = IntBuffer.wrap(scaledPixels);

        pixelBuffer = new PixelBuffer<>(scaledWidth, scaledHeight, buffer,
                PixelFormat.getIntArgbPreInstance());
        screenImage = new WritableImage(pixelBuffer);

        reset();
    }


    @Override
    public void outPort(int port, int value) {
        // Обновление цвета бордюра
        borderColor = value & 0x07;
    }

    @Override
    public void update(int cycles) {

    }

    @Override
    public void render() {

    }

    @Override
    public void markScreenDirty() {

    }

    @Override
    public void setZoomLevel(ZoomLevel newZoom) {
        currentZoom = newZoom;
    }

    @Override
    public void nextZoomLevel() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void previousZoomLevel() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ZoomLevel getCurrentZoom() {
        return currentZoom;
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        addressOnBus.set(new AddressOnBusEvent(address, tstates));
    }

    @Override
    public void ticks(long tStates, int delta) {
        while (delta > 0) {
            delta--;
            currentTState++;
            currentLineTState++;

            drawPixel(currentX, currentY);
            currentX++;
            drawPixel(currentX, currentY);
            currentX++;

            if (currentLineTState >= ULATiming.TSTATES_PER_LINE) {
                currentLineTState -= ULATiming.TSTATES_PER_LINE;
                currentY++;
                currentX = 0;
            }
            if (currentTState >= ULATiming.TSTATES_PER_FRAME) {
                if (dirtyScreen) {
                    drawSnapshot();
                }
                dirtyScreen = false;
                currentTState = 0;
                currentLineTState = 0;
                frameCounter++;
                currentY = 0;
                currentX = 0;
            }
        }
    }

    private void drawPixel(int x, int y) {
        if (x < 0 || x >= TOTAL_WIDTH || y < 0 || y >= TOTAL_HEIGHT) {
            return;
        }
        if (isScreenPixel(x, y)) {
            //drawBorderPixel(x, y, borderColor);
            drawScreenPixel(x, y, x - BORDER_H_SIZE, y - BORDER_V_SIZE);
            return;
        }
        int index = y * TOTAL_WIDTH + x;
        int color = pixels[index];
        if (color != borderColor) {
            pixels[index] = borderColor;
        } else {
            setPixel(x, y, SPECTRUM_COLORS_ARGB[color]);
        }
    }

    private void drawScreenPixel(int x, int y, int screenX, int screenY) {

        int bitX = screenX % 8; // Бит в байте

        int pixelAddr = calculatePixelAddress(screenX, screenY);
        int pixelByte = memory.readByte(pixelAddr) & 0xFF;
        int attrAddr = getAttributeAddress(screenX, screenY);
        int attrByte = memory.readByte(attrAddr) & 0xFF;

        int ink = attrByte & 0x07;
        int paper = (attrByte >> 3) & 0x07;
        int bright = (attrByte >> 6) & 0x01;
        int flash = (attrByte >> 7) & 0x01;

        // Определяем цвет пикселя
        boolean pixelSet = ((pixelByte >> (7 - bitX)) & 1) == 1;
        int color = pixelSet ? ink : paper;

        // Мерцание синхронизировано с кадрами
        if (flash == 1 && (frameCounter & 0x10) != 0) {
            color = pixelSet ? paper : ink;
        }
        if (bright == 1) color += 8;

        int index = y * TOTAL_WIDTH + x;
        int currentColor = pixels[index];
        if (currentColor != color) {
            pixels[index] = color;
            setPixel(x, y, SPECTRUM_COLORS_ARGB[color]);
        }
    }

    /**
     * Draw a pixel on the buffer in argb.
     * @param x - x coordinate
     * @param y - y coordinate
     * @param iARGB - argb color
     */
    private void setPixel(int x, int y, int iARGB) {
        dirtyScreen = true;
        int scaledX = x * currentZoom.getScale();
        int scaledY = y * currentZoom.getScale();
        int scaledWidth = TOTAL_WIDTH * currentZoom.getScale();
        for (int dy = 0; dy < currentZoom.getScale(); dy++) {
            for (int dx = 0; dx < currentZoom.getScale(); dx++) {
                int index = (scaledY + dy) * scaledWidth + (scaledX + dx);
                scaledPixels[index] = iARGB;
            }
        }
    }

    private int calculatePixelAddress(int x, int y) {
        int line = y & 0x07;          // Младшие 3 бита Y
        int third = (y >> 3) & 0x07;  // Средние 3 бита Y
        int section = (y >> 6) & 0x03; // Старшие 2 бита Y
        int column = x >> 3;           // X / 8

        return BITMAP_START + (section << 11) + (line << 8) + (third << 5) + column;
    }

    private int getAttributeAddress(int x, int y) {
        int row = y >> 3;    // Y / 8
        int col = x >> 3;    // X / 8
        return ATTR_START + (row << 5) + col;
    }

    private boolean isScreenPixel(int x, int y) {
        return x >= BORDER_H_SIZE && x < BORDER_H_SIZE + SCREEN_WIDTH
                && y >= BORDER_V_SIZE && y < BORDER_V_SIZE + SCREEN_HEIGHT;
    }

    @Override
    public void reset() {
        currentTState = 0;
        currentTState = 0;
        currentX = 0;
        currentY = 0;
        clearScreen();
    }

    private void clearScreen() {
        borderColor = 0;
        Arrays.fill(pixels, 0);
        Arrays.fill(scaledPixels, SPECTRUM_COLORS_ARGB[0]);
        dirtyScreen = false;
        drawSnapshot();
    }

    private void drawSnapshot() {
        Platform.runLater(() -> {
            pixelBuffer.updateBuffer(pb -> null);
            gc.drawImage(screenImage, 0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    @Override
    public void start() {
        renderThread = new Thread(this::renderThread);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    @Override
    public void stop() {
        renderThread.interrupt();
    }

    private void renderThread() {
        while (true) {
            render();
            try {
                Thread.sleep(1000 / 60); // 60 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class AddressOnBusEvent {
        public final int address;
        public final int tstates;
    }

}
