package spectrum.hardware.debug;

import spectrum.hardware.machine.HardwareProvider;

public interface DebugManager {

    void preExecuteCheck(HardwareProvider hardwareProvider);

    void postExecuteCheck(HardwareProvider hardwareProvider);

    void resume();

    void pause();

    void stepInto();

    void addBreakpoint(int address);

    boolean isBreakpoint(int address);

    boolean hasBreakpoints();

    void removeBreakpoint(int address);

    void clearAllBreakpoints();

    void setDebugListener(DebugListener debugListener);

    boolean isPaused();

}
