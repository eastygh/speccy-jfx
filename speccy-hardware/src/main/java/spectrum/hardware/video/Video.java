package spectrum.hardware.video;

import spectrum.hardware.machine.Device;
import spectrum.hardware.ula.ClockListener;
import spectrum.hardware.ula.OutPortListener;

/**
 * Video interface for Spectrum hardware.
 */
public interface Video extends Device, ClockListener, OutPortListener {

    int SCREEN_WIDTH = 256;
    int SCREEN_HEIGHT = 192;
    int BORDER_SIZE = 48;
    int BORDER_V_SIZE = 48; // Vertical border size
    int BORDER_H_SIZE = 48; // Horizontal border size
    int TOTAL_WIDTH = SCREEN_WIDTH + BORDER_H_SIZE * 2;
    int TOTAL_HEIGHT = SCREEN_HEIGHT + BORDER_V_SIZE * 2;

    void setVideoDriver(VideoDriver videoDriver);

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

    // Original screen size
    default int getScreenWidth() {
        return SCREEN_WIDTH;
    }

    default int getScreenHeight() {
        return SCREEN_HEIGHT;
    }

    default int getBorderSize() {
        return BORDER_SIZE;
    }

}
