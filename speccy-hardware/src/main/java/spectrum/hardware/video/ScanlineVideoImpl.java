package spectrum.hardware.video;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;
import spectrum.hardware.machine.MachineSettings;
import spectrum.hardware.memory.Memory;

@Slf4j
public class ScanlineVideoImpl implements Video {

    // Screen memory addresses
    public static final int BITMAP_START = 0x4000;
    public static final int BITMAP_SIZE = 6144;   // 256*192/8
    public static final int ATTR_START = 0x5800;
    public static final int ATTR_SIZE = 768;      // 32*24

    private final Memory memory;
    private final MachineTypes machineType;

    private int borderColor = 0;
    private long currentTState = 0;
    private int frameCounter = 0;
    private int currentLineTState = 0;
    private int currentX = 0;
    private int currentY = 0;
    private boolean dirtyScreen = false;

    @Setter
    private VideoDriver videoDriver;

    private final int[] pixels = new int[TOTAL_HEIGHT * TOTAL_WIDTH];

    public ScanlineVideoImpl(VideoDriver videoDriver, Memory memory, MachineSettings machineSettings) {
        this.videoDriver = videoDriver;
        this.memory = memory;
        this.machineType = machineSettings.getMachineType();
    }

    @Override
    public void outPort(int port, int value) {
        if ((port & 0xFF) == 0xFE) {
            borderColor = value & 0x07;
        }
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void reset() {
        // do nothing
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
    public void endFrame() {
        currentTState = 0;
        currentLineTState = 0;
        currentY = 0;
        currentX = 0;
        frameCounter++;
        if (dirtyScreen) {
            dirtyScreen = false;
            if (videoDriver != null) {
                videoDriver.refreshScreen();
            }
        }
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

            if (currentLineTState >= machineType.tstatesLine) {
                currentLineTState -= machineType.tstatesLine;
                currentY++;
                currentX = 0;
            }
        }
    }

    private void drawPixel(int x, int y) {
        if (x < 0 || x >= TOTAL_WIDTH || y < 0 || y >= TOTAL_HEIGHT) {
            return;
        }
        if (isScreenPixel(x, y)) {
            drawScreenPixel(x, y, x - BORDER_H_SIZE, y - BORDER_V_SIZE);
        } else {
            drawBorderPixel(x, y);
        }
    }

    private void drawBorderPixel(int x, int y) {
        int index = y * TOTAL_WIDTH + x;
        int color = pixels[index];
        if (color != borderColor) {
            pixels[index] = borderColor;
        } else {
            setPixel(x, y, color);
        }
    }

    private void drawScreenPixel(int x, int y, int screenX, int screenY) {

        int bitX = screenX % 8; // bit in byte

        int pixelAddr = calculatePixelAddress(screenX, screenY);
        int pixelByte = memory.readByte(pixelAddr) & 0xFF;
        int attrAddr = getAttributeAddress(screenX, screenY);
        int attrByte = memory.readByte(attrAddr) & 0xFF;

        int ink = attrByte & 0x07;
        int paper = (attrByte >> 3) & 0x07;
        int bright = (attrByte >> 6) & 0x01;
        int flash = (attrByte >> 7) & 0x01;

        // Decode pixel color
        boolean pixelSet = ((pixelByte >> (7 - bitX)) & 1) == 1;
        int color = pixelSet ? ink : paper;

        // flashing
        if (flash == 1 && (frameCounter & 0x10) != 0) {
            color = pixelSet ? paper : ink;
        }
        if (bright == 1) color += 8;

        int index = y * TOTAL_WIDTH + x;
        int currentColor = pixels[index];
        if (currentColor != color) {
            pixels[index] = color;
            setPixel(x, y, color);
        }
    }

    private int getAttributeAddress(int x, int y) {
        int row = y >> 3;    // Y / 8
        int col = x >> 3;    // X / 8
        return ATTR_START + (row << 5) + col;
    }

    private int calculatePixelAddress(int x, int y) {
        int line = y & 0x07;          // Little 3 bits Y
        int third = (y >> 3) & 0x07;  // Middle 3 bits Y
        int section = (y >> 6) & 0x03; // Big 2 bits Y
        int column = x >> 3;           // X / 8

        return BITMAP_START + (section << 11) + (line << 8) + (third << 5) + column;
    }

    public static boolean isScreenPixel(int x, int y) {
        return x >= BORDER_H_SIZE && x < BORDER_H_SIZE + SCREEN_WIDTH
                && y >= BORDER_V_SIZE && y < BORDER_V_SIZE + SCREEN_HEIGHT;
    }

    private void setPixel(int x, int y, int color) {
        dirtyScreen = true;
        if (videoDriver == null) {
            return;
        }
        videoDriver.drawPixel(x, y, color);
    }

}
