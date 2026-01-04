package spectrum.jfx.hardware.machine;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import machine.MachineTypes;

@Data
@Builder
@Accessors(chain = true)
public class MachineSettings {

    private boolean ulaAddTStates;
    private MachineTypes machineType;
    private int audioSampleRate;

    private CpuImplementation cpuImplementation;

    public static MachineSettings ofDefault(CpuImplementation cpuImplementation) {
        if (cpuImplementation == null) {
            cpuImplementation = CpuImplementation.SANCHES;
        }
        return MachineSettings.
                builder()
                .machineType(MachineTypes.SPECTRUM48K)
                .audioSampleRate(44100)
                .ulaAddTStates(cpuImplementation.isUlaAddTStates())
                .cpuImplementation(cpuImplementation)
                .build();
    }

    public static MachineSettings ofDefault() {
        return ofDefault(null);
    }

}
