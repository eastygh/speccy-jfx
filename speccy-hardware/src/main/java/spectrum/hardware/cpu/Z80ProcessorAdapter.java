package spectrum.hardware.cpu;

import com.codingrodent.microprocessor.z80.Z80Core;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.machine.CpuImplementation;
import spectrum.hardware.snapshot.CPUSnapShot;
import spectrum.hardware.ula.Ula;
import z80core.NotifyOps;

import java.util.BitSet;

import static com.codingrodent.microprocessor.z80.CPUConstants.RegisterNames.*;

@Slf4j
public class Z80ProcessorAdapter extends Z80Core implements spectrum.hardware.cpu.CPU {

    private final Ula ula;
    private final NotifyOps notifyOps;
    protected final BitSet breakpointAt = new BitSet(65536);

    public Z80ProcessorAdapter(Ula ula, NotifyOps notify) {
        super(ula, ula);
        this.notifyOps = notify;
        this.ula = ula;
    }

    @Override
    public int executeInstruction() {
        long startTStates = getTStates();
        executeOneInstruction0();
        return (int) (getTStates() - startTStates);
    }

    @Override
    public int executeInstruction(int tStatesLimit) {
        int startTStates = (int) getTStates();
        int tillTStates = startTStates + tStatesLimit;
        while (getTStates() < tillTStates) {
            executeOneInstruction0();
        }
        return (int) (getTStates() - startTStates);
    }

    @Override
    public boolean isBreakpoint(int address) {
        return breakpointAt.get(address);
    }

    @Override
    public void setBreakpoint(int address, boolean state) {
        breakpointAt.set(address, state);
    }

    @Override
    public void resetBreakpoints() {
        breakpointAt.clear();
    }

    @Override
    public void setRegA(int value) {
        setRegisterValue(A, value);
    }

    @Override
    public int getRegA() {
        return getRegisterValue(A);
    }

    @Override
    public void setFlags(int value) {
        setRegisterValue(F, value);
    }

    @Override
    public int getFlags() {
        return getRegisterValue(F);
    }

    @Override
    public void setRegPC(int address) {
        setProgramCounter(address);
    }

    @Override
    public int getRegSP() {
        return getRegisterValue(SP);
    }

    @Override
    public void setRegSP(int address) {
        setRegisterValue(SP, address);
    }

    @Override
    public int getRegIX() {
        return getRegisterValue(IX);
    }

    @Override
    public void setRegIX(int value) {
        setRegisterValue(IX, value);
    }

    @Override
    public int getRegIY() {
        return getRegisterValue(IY);
    }

    @Override
    public void setRegIY(int value) {
        setRegisterValue(IY, value);
    }

    @Override
    public int getRegDE() {
        return getRegisterValue(DE);
    }

    @Override
    public void setRegDE(int value) {
        setRegisterValue(DE, value);
    }

    @Override
    public int getRegHL() {
        return getRegisterValue(HL);
    }

    @Override
    public void setRegHL(int value) {
        setRegisterValue(HL, value);
    }

    @Override
    public void setRegBC(int value) {
        setRegisterValue(BC, value);
    }

    @Override
    public int getRegBC() {
        return getRegisterValue(BC);
    }

    @Override
    public void setCarryFlag(boolean state) {
        setRegisterValue(F, state ? (getRegisterValue(F) | 0x01) : (getRegisterValue(F) & 0xfe));
    }

    @Override
    public boolean isCarryFlag() {
        return (getRegisterValue(F) & 0x01) != 0;
    }

    @Override
    public int getRegPC() {
        return getProgramCounter();
    }

    @Override
    public void reset() {
        resetBreakpoints();
        super.reset();
    }

    @Override
    protected int fetchOpCode(int address) {
        int opCode = ula.fetchOpcode(address);
        if (breakpointAt.get(address)) {
            opCode = notifyOps.breakpoint(address, opCode);
        }
        return opCode;
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void open() {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public CPUSnapShot getSnapShot() {
        int bc = getRegisterValue(BC);
        int de = getRegisterValue(DE);
        int hl = getRegisterValue(HL);
        int bcx = getRegisterValue(BC_ALT);
        int dex = getRegisterValue(DE_ALT);
        int hlx = getRegisterValue(HL_ALT);

        return CPUSnapShot.builder()
                .regA(getRegisterValue(A))
                .regF(getRegisterValue(F))
                .regB((bc >> 8) & 0xFF)
                .regC(bc & 0xFF)
                .regD((de >> 8) & 0xFF)
                .regE(de & 0xFF)
                .regH((hl >> 8) & 0xFF)
                .regL(hl & 0xFF)
                .regAx(getRegisterValue(A_ALT))
                .regFx(getRegisterValue(F_ALT))
                .regBx((bcx >> 8) & 0xFF)
                .regCx(bcx & 0xFF)
                .regDx((dex >> 8) & 0xFF)
                .regEx(dex & 0xFF)
                .regHx((hlx >> 8) & 0xFF)
                .regLx(hlx & 0xFF)
                .regPC(getProgramCounter())
                .regIX(getRegisterValue(IX))
                .regIY(getRegisterValue(IY))
                .regSP(getSP())
                .regI(getRegisterValue(I))
                .regR(getRegisterValue(R))
                .ffIFF1(IFF1)
                .ffIFF2(IFF2)
                .pendingEI(EIDIFlag)
                .activeNMI(NMI_FF)
                .activeINT(ula != null && ula.isActiveINT())
                .modeINT(getInterruptMode())
                .halted(halt)
                .cpuImplementation(CpuImplementation.CODINGRODENT)
                .build();
    }

    public void softPush(int value) {
        int sp = getSP();
        sp = (sp - 2) & 0xFFFF;
        ula.writeWord(sp, value & 0xFFFF);
        setRegSP(sp);
    }

    private void executeOneInstruction0() {
        executeOneInstruction();
        if (ula != null && IFF1 && ula.isActiveINT()) {
            // an additional 13 T-states by interrupt call
            ula.addTStates(13);
            callInterrupt();
        }
    }

    void callInterrupt() {

        int r = getRegisterValue(R);
        setRegisterValue(R, (r + 1) & 0x7F | (r & 0x80));
        IFF1 = false;
        IFF2 = false;
        int pc = getProgramCounter();
        if (halt) {
            pc++;
            halt = false;
        }
        softPush(pc);
        if (interruptMode == 0 || interruptMode == 1) {
            setProgramCounter(0x0038);
        } else if (interruptMode == 2) {
            //setProgramCounter(0x0066);
        }

    }

}
