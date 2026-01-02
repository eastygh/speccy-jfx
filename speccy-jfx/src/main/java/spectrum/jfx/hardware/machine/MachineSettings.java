package spectrum.jfx.hardware.machine;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import machine.MachineTypes;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.cpu.Z80WrapperImpl;

@Data
@Builder
@Accessors(chain = true)
public class MachineSettings {

    private boolean ulaAddTStates;
    private MachineTypes machineType;
    private int audioSampleRate;

    private Class<? extends CPU> cpuClass;

    public static MachineSettings ofDefault() {
        return MachineSettings.
                builder()
                .machineType(MachineTypes.SPECTRUM48K)
                .audioSampleRate(44100)
                .ulaAddTStates(true)
                .cpuClass(Z80WrapperImpl.class)
                .build();
    }

}
