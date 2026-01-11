package spectrum.jfx.hardware.machine;

import spectrum.jfx.hardware.cpu.AddressHookListener;
import spectrum.jfx.hardware.disk.DiskController;

public interface Emulator {

    void start();

    void stop();

    void reset();

    void pause();

    void resume();

    boolean isHold();

    boolean waitForHold();

    long getFrames();

    AddressHookListener addBreakPointListener(int address, AddressHookListener listener);

    boolean removeBreakPointListener(int address);

    void addExternalTask(Runnable task);

    void loadRom(String fullName);

    void setSpeedUpMode(boolean speedUp);

    MachineSettings getMachineSettings();

    DiskController getDiskController();

}
