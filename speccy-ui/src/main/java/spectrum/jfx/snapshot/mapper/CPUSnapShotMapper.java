package spectrum.jfx.snapshot.mapper;

import lombok.experimental.UtilityClass;
import snapshots.Z80State;
import spectrum.jfx.snapshot.CPUSnapShot;
import z80core.Z80.IntMode;

@UtilityClass
public class CPUSnapShotMapper {

    public static Z80State toZ80State(CPUSnapShot snapshot) {
        if (snapshot == null) {
            return null;
        }

        Z80State state = new Z80State();
        state.setRegA(snapshot.getRegA());
        state.setRegB(snapshot.getRegB());
        state.setRegC(snapshot.getRegC());
        state.setRegD(snapshot.getRegD());
        state.setRegE(snapshot.getRegE());
        state.setRegH(snapshot.getRegH());
        state.setRegL(snapshot.getRegL());
        state.setRegF(snapshot.getRegF());
        state.setFlagQ(snapshot.isFlagQ());

        state.setRegAx(snapshot.getRegAx());
        state.setRegFx(snapshot.getRegFx());
        state.setRegBx(snapshot.getRegBx());
        state.setRegCx(snapshot.getRegCx());
        state.setRegDx(snapshot.getRegDx());
        state.setRegEx(snapshot.getRegEx());
        state.setRegHx(snapshot.getRegHx());
        state.setRegLx(snapshot.getRegLx());

        state.setRegPC(snapshot.getRegPC());
        state.setRegIX(snapshot.getRegIX());
        state.setRegIY(snapshot.getRegIY());
        state.setRegSP(snapshot.getRegSP());
        state.setRegI(snapshot.getRegI());
        state.setRegR(snapshot.getRegR());

        state.setIFF1(snapshot.isFfIFF1());
        state.setIFF2(snapshot.isFfIFF2());
        state.setPendingEI(snapshot.isPendingEI());
        state.setNMI(snapshot.isActiveNMI());
        state.setINTLine(snapshot.isActiveINT());
        state.setIM(IntMode.values()[snapshot.getModeINT()]);
        state.setHalted(snapshot.isHalted());
        state.setMemPtr(snapshot.getMemptr());

        return state;
    }

    public static CPUSnapShot toCPUSnapShot(Z80State state) {
        if (state == null) {
            return null;
        }

        return CPUSnapShot.builder()
                .regA(state.getRegA())
                .regB(state.getRegB())
                .regC(state.getRegC())
                .regD(state.getRegD())
                .regE(state.getRegE())
                .regH(state.getRegH())
                .regL(state.getRegL())
                .regF(state.getRegF())
                .flagQ(state.isFlagQ())
                .regAx(state.getRegAx())
                .regFx(state.getRegFx())
                .regBx(state.getRegBx())
                .regCx(state.getRegCx())
                .regDx(state.getRegDx())
                .regEx(state.getRegEx())
                .regHx(state.getRegHx())
                .regLx(state.getRegLx())
                .regPC(state.getRegPC())
                .regIX(state.getRegIX())
                .regIY(state.getRegIY())
                .regSP(state.getRegSP())
                .regI(state.getRegI())
                .regR(state.getRegR())
                .ffIFF1(state.isIFF1())
                .ffIFF2(state.isIFF2())
                .pendingEI(state.isPendingEI())
                .activeNMI(state.isNMI())
                .activeINT(state.isINTLine())
                .modeINT(state.getIM().ordinal())
                .halted(state.isHalted())
                .memptr(state.getMemPtr())
                .build();
    }
}
