package spectrum.jfx.hardware.machine;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import machine.MachineTypes;

@Data
@Builder
@Accessors(chain = true)
public class MachineSettings {

    private MachineTypes machineType = MachineTypes.SPECTRUM48K;
    private int audioSampleRate = 44100;

}
