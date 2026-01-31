package spectrum.hardware.debug;

import spectrum.hardware.machine.HardwareProvider;

public interface DebugListener {

    void onStepComplete(HardwareProvider hv, SuspendType suspendType);

    void onResumed();

}
