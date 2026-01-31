package spectrum.hardware.factory;

import lombok.experimental.UtilityClass;
import spectrum.hardware.cpu.CPU;
import spectrum.hardware.cpu.Z80CoreAdapter;
import spectrum.hardware.cpu.Z80ProcessorAdapter;
import spectrum.hardware.machine.CpuImplementation;
import spectrum.hardware.machine.MachineSettings;
import spectrum.hardware.ula.Ula;
import z80core.NotifyOps;

@UtilityClass
public class CPUFactory {

    public static CPU createCPU(MachineSettings machineSettings, Ula ula, NotifyOps notify) {
        if (machineSettings.getCpuImplementation() == CpuImplementation.SANCHES) {
            return new Z80CoreAdapter(ula, notify);
        }
        if (machineSettings.getCpuImplementation() == CpuImplementation.CODINGRODENT) {
            return new Z80ProcessorAdapter(ula, notify);
        } else {
            throw new IllegalArgumentException("Unsupported CPU implementation");
        }
    }

}
