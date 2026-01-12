package spectrum.jfx.hardware.factory;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.disk.DiskController;
import spectrum.jfx.hardware.disk.wd1793.WD1793Impl;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.ula.Ula;
import spectrum.jfx.hardware.util.EmulatorUtils;

import java.io.IOException;

@Slf4j
@UtilityClass
public class DiskFactory {

    public static DiskController createDiskController(MachineSettings machineSettings, Ula ula) {
        if (!machineSettings.isEnableDiskController()) {
            return null;
        }
        DiskController diskController;
        if (machineSettings.getDiskControllerType() == MachineSettings.DiskControllerTypes.WD1793
                || machineSettings.getDiskControllerType() == null) {
            diskController = new WD1793Impl(machineSettings);
            if (!diskController.initWithULA(ula)) {
                log.error("Failed to initialize disk controller with ULA");
            }
        } else {
            throw new IllegalArgumentException("Unsupported disk controller type: " + machineSettings.getDiskControllerType());
        }

        byte[] diskData = null;
        byte[] diskData2 = null;
        try {
            diskData2 = EmulatorUtils.loadFile("disk/38_exolon.scl");
            diskData = EmulatorUtils.loadFile("disk/BubLand2.trd");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        diskController.loadDisk(0, diskData);
        diskController.loadDisk(1, new byte[655360]);
        diskController.loadDisk(2, diskData2);

        return diskController;
    }


}
