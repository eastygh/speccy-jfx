package spectrum.jfx.hardware.sound;

import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.OutPortListener;

public interface Sound extends OutPortListener, ClockListener, Device {

    // 44.1 kHz
    int SAMPLE_RATE = 44100;
    short BEEPER_AMPLITUDE = 8000;

    double getVolume();

    void setVolume(double volume);

    void pushBackTape(boolean state);

    void mute(boolean state);

    /**
     * Play the sound by external thread
     * for example - main loop
     */
    void play(int cycles);

    /**
     * End of frame, called by main loop
     */
    void endFrame();

}
