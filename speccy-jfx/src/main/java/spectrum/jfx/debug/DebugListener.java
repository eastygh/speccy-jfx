package spectrum.jfx.debug;

import spectrum.jfx.hardware.machine.HardwareProvider;

public interface DebugListener {

    void onStepComplete(HardwareProvider hv, SuspendType suspendType);

    void onResumed();

}
