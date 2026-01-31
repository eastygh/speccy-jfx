package spectrum.hardware.ula;

import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.machine.MachineSettings;

@Slf4j
public class FloatingBus implements InPortListener {

    private final MachineSettings machineSettings;
    private final int[] floatingBusTable;

    public FloatingBus(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        this.floatingBusTable = new int[machineSettings.getMachineType().tstatesFrame];
    }

    @Override
    public int inPort(int port) {
        log.trace("inPort floating bus: port={}", String.format("%04X", port));
        return 0xFF;
    }

}
