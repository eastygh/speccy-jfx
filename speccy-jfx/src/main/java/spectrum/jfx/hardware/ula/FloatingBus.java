package spectrum.jfx.hardware.ula;

import lombok.RequiredArgsConstructor;
import spectrum.jfx.hardware.machine.MachineSettings;

public class FloatingBus implements InPortListener {

    private final MachineSettings machineSettings;
    private final int[] floatingBusTable;

    public FloatingBus(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        this.floatingBusTable = new int[machineSettings.getMachineType().tstatesFrame];
    }

    @Override
    public int inPort(int port) {
        return 0xFF;
    }

}
