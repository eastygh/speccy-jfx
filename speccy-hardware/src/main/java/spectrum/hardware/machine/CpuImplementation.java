package spectrum.hardware.machine;

import lombok.Getter;

public enum CpuImplementation {

    SANCHES(true),
    CODINGRODENT(false);

    @Getter
    private final boolean ulaAddTStates;

    CpuImplementation(boolean ulaAddTStates) {
        this.ulaAddTStates = ulaAddTStates;
    }

}
