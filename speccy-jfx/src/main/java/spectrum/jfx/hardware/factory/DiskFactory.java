package spectrum.jfx.hardware.factory;

import lombok.experimental.UtilityClass;
import spectrum.jfx.hardware.disk.DiskController;
import spectrum.jfx.hardware.disk.TRDOSController;
import spectrum.jfx.hardware.disk.WD1793Impl;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.hardware.ula.Ula;
import spectrum.jfx.hardware.util.EmulatorUtils;

import java.io.IOException;

@UtilityClass
public class DiskFactory {

    public static DiskController createDiskController(MachineSettings machineSettings, Ula ula) {
        if (!machineSettings.isEnableTRDOS()) {
            return null;
        }
        //DiskController diskController = new WD1793(machineSettings);
        //DiskController diskController = new WD1593GImpl();
        DiskController diskController = new WD1793Impl();
        assignPorts(diskController, ula);
        assignClock(diskController, ula);
        byte[] diskData = null;
        try {
            //diskData = EmulatorUtils.loadFile("disk/38_exolon.scl");
            diskData = EmulatorUtils.loadFile("disk/BubLand2.trd");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        diskController.loadDisk(0, diskData);
        diskController.loadDisk(1, diskData);
        diskController.loadDisk(2, diskData);
        diskController.loadDisk(3, diskData);
        // Add TRDOS controller (ROM Switcher)
        ula.addAddressHookController(new TRDOSController(machineSettings, diskController, ula.getMemory()));
        return diskController;
    }

    private static void assignClock(DiskController diskController, Ula ula) {
        ula.addClockListener(diskController);
    }

    private static void assignPorts(DiskController diskController, Ula ula) {
        /*
         * InPorts 0x1F, 0x3F,0x5F,0x7F,0xFF
         */
        ula.addPortListener(0x1F, (InPortListener) diskController);
        ula.addPortListener(0x3F, (InPortListener) diskController);
        ula.addPortListener(0x5F, (InPortListener) diskController);
        ula.addPortListener(0x7F, (InPortListener) diskController);
        ula.addPortListener(0xFF, (InPortListener) diskController);

        /*
         * OutPorts 0x1F,0x3F,0x5F,0x7F,0xFF
         */
        ula.addPortListener(0x1F, (OutPortListener) diskController);
        ula.addPortListener(0x3F, (OutPortListener) diskController);
        ula.addPortListener(0x5F, (OutPortListener) diskController);
        ula.addPortListener(0x7F, (OutPortListener) diskController);
        ula.addPortListener(0xFF, (OutPortListener) diskController);
    }

}
