package spectrum.jfx.hardware.cpu;

import com.codingrodent.microprocessor.IBaseDevice;
import com.codingrodent.microprocessor.IMemory;
import com.codingrodent.microprocessor.z80.Z80Core;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.ula.Ula;
import z80core.NotifyOps;

import java.util.BitSet;

import static com.codingrodent.microprocessor.z80.CPUConstants.RegisterNames.*;

@Slf4j
public class Z80ProcessorAdapter extends Z80Core implements CPU {

    private final IMemory ram;
    private final Ula ula;
    private final NotifyOps notifyOps;
    protected final BitSet breakpointAt = new BitSet(65536);

    public Z80ProcessorAdapter(IMemory ram, IBaseDevice io, NotifyOps notify) {
        super(ram, io);
        this.notifyOps = notify;
        this.ram = ram;
        if (Ula.class.isAssignableFrom(ram.getClass())) {
            ula = (Ula) ram;
        } else {
            ula = null;
        }
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
        int opCode = ram.fetchOpCode(address);
        if (breakpointAt.get(address)) {
            opCode = notifyOps.breakpoint(address, opCode);
        }
        return opCode;
    }

    public void softPush(int value) {
        int sp = getSP();
        sp = (sp - 2) & 0xFFFF;
        ram.writeWord(sp, value & 0xFFFF);
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

//    void handleInterrupt() {
//
//        iff1 = false;
//        iff2 = false;
//
//        // Затраты времени: стандартный цикл прерывания занимает ~13 T-states
//        addTStates(13);
//
//        pushStack(pc); // Сначала старший байт, потом младший
//
//        if (interruptMode == 1) {
//            pc = 0x0038;
//        } else if (interruptMode == 2) {
//            int vector = bus.readInterruptVector(); // Обычно возвращает 0xFF на Spectrum
//            int addressTablePointer = (i << 8) | vector;
//            pc = memory.readWord(addressTablePointer);
//        }
//        // Mode 0 реализуется через fetch и выполнение инструкции с шины
//    }

}
