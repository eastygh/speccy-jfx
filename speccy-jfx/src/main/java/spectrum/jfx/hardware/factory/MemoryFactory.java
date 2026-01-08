package spectrum.jfx.hardware.factory;

import lombok.experimental.UtilityClass;
import machine.MachineTypes;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.memory.Memory128KImpl;
import spectrum.jfx.hardware.memory.Memory64KImpl;

@UtilityClass
public class MemoryFactory {

    public static Memory createMemory(MachineSettings machineSettings) {
        Memory memory;
        if (machineSettings.getMachineType() == MachineTypes.SPECTRUM48K) {
            memory = new Memory64KImpl(machineSettings);
        } else if (machineSettings.getMachineType() == MachineTypes.SPECTRUM128K) {
            memory = new Memory128KImpl(machineSettings);
        } else {
            throw new IllegalArgumentException("Unsupported machine type");
        }
        memory.init();
        memory.loadRoms();
        return memory;

    }

}
