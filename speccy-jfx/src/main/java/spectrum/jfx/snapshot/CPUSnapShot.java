package spectrum.jfx.snapshot;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
public class CPUSnapShot implements SnapShot {

    private int regA, regB, regC, regD, regE, regH, regL;
    private int regF;
    private boolean flagQ;
    private int regAx;
    private int regFx;
    private int regBx, regCx, regDx, regEx, regHx, regLx;
    private int regPC;
    private int regIX;
    private int regIY;
    private int regSP;
    private int regI;
    private int regR;
    private boolean ffIFF1;
    private boolean ffIFF2;
    private boolean pendingEI;
    private boolean activeNMI;
    private boolean activeINT;
    private int modeINT;
    private boolean halted;
    private int memptr;

}
