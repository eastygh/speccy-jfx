package spectrum.jfx.hardware.machine;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import machine.MachineTypes;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@Accessors(chain = true)
public class MachineSettings {

    private boolean ulaAddTStates;
    private MachineTypes machineType;
    private int audioSampleRate;
    private boolean enableTRDOS;
    private String trDOSRomFilePath;
    private String romFilePath01;
    private String romFilePath02;

    @SuppressWarnings("unused")
    public String getRomFilePath01() {
        if (StringUtils.isEmpty(romFilePath01)) {
            switch (machineType) {
                case SPECTRUM48K -> romFilePath01 = "/roms/48.rom";
                case SPECTRUM128K -> romFilePath01 = "/roms/128-0.rom";
                default -> romFilePath01 = "";
            }
        }
        return romFilePath01;
    }

    @SuppressWarnings("unused")
    public String getRomFilePath02() {
        if (StringUtils.isEmpty(romFilePath02)) {
            switch (machineType) {
                case SPECTRUM128K -> romFilePath02 = "/roms/128-1.rom";
                default -> romFilePath02 = "";
            }
        }
        return romFilePath02;
    }

    @SuppressWarnings("unused")
    public String getTrDOSRomFilePath() {
        if (StringUtils.isEmpty(trDOSRomFilePath)) {
            trDOSRomFilePath = "/roms/trdos.rom";
        }
        return trDOSRomFilePath;
    }


    private CpuImplementation cpuImplementation;

    public static MachineSettings ofDefault(CpuImplementation cpuImplementation) {
        if (cpuImplementation == null) {
            cpuImplementation = CpuImplementation.SANCHES;
        }
        return MachineSettings.
                builder()
                .machineType(MachineTypes.SPECTRUM48K)
                .audioSampleRate(44100)
                .enableTRDOS(true)
                .ulaAddTStates(cpuImplementation.isUlaAddTStates())
                .cpuImplementation(cpuImplementation)
                .build();
    }

    public static MachineSettings ofDefault() {
        return ofDefault(null);
    }

}
