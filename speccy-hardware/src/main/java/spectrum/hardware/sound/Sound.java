package spectrum.hardware.sound;

import spectrum.hardware.machine.Device;
import spectrum.hardware.ula.ClockListener;
import spectrum.hardware.ula.OutPortListener;

public interface Sound extends OutPortListener, ClockListener, Device {

    // 44.1 kHz
    int SAMPLE_RATE = 44100;
    short BEEPER_AMPLITUDE = 8000;

    double getVolume();

    void setVolume(double volume);

    /**
     * Triggered beeper state.
     * example - for tape sound emulation
     */
    void pushBackTape(boolean state);

    void mute(boolean state);

    /**
     * Play the sound by external thread.
     * for example - main loop
     */
    void play(int cycles);

    /**
     * End of frame, called by main loop
     */
    void endFrame();

    /**
     * Write PCM data to the sound output, from an external thread.
     *
     * @param data PCM data array
     */
    default void write(short[] data) {
        // no-op
    }

    /**
     * Write PCM data to the sound output, from an external thread.
     *
     * @param value PCM sample value
     */
    default void write(short value) {
        // no-op
    }

}
