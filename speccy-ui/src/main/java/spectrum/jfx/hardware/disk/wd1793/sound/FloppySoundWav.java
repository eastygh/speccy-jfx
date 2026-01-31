package spectrum.jfx.hardware.disk.wd1793.sound;

import spectrum.jfx.hardware.disk.wd1793.ControllerState;
import spectrum.hardware.sound.Sound;

public final class FloppySoundWav implements FloppySoundEngine {

    private final Sound sound;
    private final short[] loop;

    private int pos;
    private volatile boolean playing;
    private ControllerState lastState = ControllerState.IDLE;

    public FloppySoundWav(Sound sound, short[] loop) {
        this.sound = sound;
        this.loop = loop;
    }

    @Override
    public void ticks(long tStates, ControllerState state, boolean writeMode) {

        if (lastState == ControllerState.IDLE && state != ControllerState.IDLE) {
            pos = 0;
            playing = true;
        }

        if (state == ControllerState.IDLE) {
            playing = false;
        }

        lastState = state;
    }

    // ВАЖНО: этот метод должен вызываться регулярно,
    // например раз в frame или из аудиопотока
    @Override
    public void step(int samples) {

        if (!playing) {
            return;
        }

        for (int i = 0; i < samples; i++) {
            sound.write(loop[pos++]);
            if (pos >= loop.length) {
                pos = 0;
            }
        }
    }

    @Override public void init() {}
    @Override public void reset() { pos = 0; playing = false; }
    @Override public void open() {}
    @Override public void close() {}
}
