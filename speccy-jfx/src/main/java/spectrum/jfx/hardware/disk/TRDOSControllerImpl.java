package spectrum.jfx.hardware.disk;

import lombok.SneakyThrows;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.ula.AddressHookController;
import spectrum.jfx.hardware.util.EmulatorUtils;

import java.util.Objects;

import static spectrum.jfx.hardware.memory.Memory.ROM_END;

public class TRDOSControllerImpl implements AddressHookController, TRDOSController {

    public static final int TR_DOS_ENTRY_POINT_MASK = 0x3D00;

    private static byte[] romData;
    private static String trdosRomFilePath;
    private final MachineSettings machineSettings;
    private final Memory memory;
    private final DiskController diskController;
    private volatile boolean trdosROMEnabled = false;

    public TRDOSControllerImpl(
            MachineSettings machineSettings,
            DiskController diskController,
            Memory memory
    ) {
        this.machineSettings = machineSettings;
        this.memory = memory;
        this.diskController = diskController;
        getRomData(machineSettings);
    }

    @SneakyThrows
    public synchronized byte[] getRomData(MachineSettings machineSettings) {
        if (!Objects.equals(trdosRomFilePath, machineSettings.getTrDOSRomFilePath())) {
            romData = EmulatorUtils.loadFile(machineSettings.getTrDOSRomFilePath());
            trdosRomFilePath = machineSettings.getTrDOSRomFilePath();
        }
        return romData;
    }

    @Override
    public void checkAddress(int address) {
        trDOSSwitcher(address);
    }

    private void trDOSSwitcher(int address) {
        if (!machineSettings.isEnableTRDOS()) {
            return;
        }
        if (trdosROMEnabled && address > ROM_END) {
            // Switch to ZX Spectrum ROM
            switchToZXSpectrum();
        } else if (!trdosROMEnabled && ((address & 0xFF00) == TR_DOS_ENTRY_POINT_MASK)) {
            // Switch to TR-DOS ROM
            switchToTRDOS();
        }
    }

    @Override
    public synchronized void switchToTRDOS() {
        trdosROMEnabled = true;
        memory.mapBank(0, getRomData(machineSettings));
        diskController.setActive(true);
    }

    @Override
    public synchronized void switchToZXSpectrum() {
        trdosROMEnabled = false;
        memory.unmapBank(0);
        diskController.setActive(false);
    }

}
