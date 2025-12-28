package spectrum.jfx.hardware.machine;

import spectrum.jfx.hardware.cpu.BreakPointListener;

public interface Emulator {

    void start();

    void stop();

    void reset();

    void pause();

    void resume();

    boolean isHold();

    long getFrames();

    BreakPointListener addBreakPointListener(int address, BreakPointListener listener);

    boolean removeBreakPointListener(int address);

    void addExternalTask(Runnable task);

    void loadRom(String fullName);

    void setSpeedUpMode(boolean speedUp);

}
