package spectrum.jfx.hardware.cpu;

import com.codingrodent.microprocessor.IBaseDevice;
import com.codingrodent.microprocessor.IMemory;
import com.codingrodent.microprocessor.Z80.Z80Core;

import static com.codingrodent.microprocessor.Z80.CPUConstants.RegisterNames.*;


public class Z80CoreAdapter extends Z80Core implements CPU {

    public Z80CoreAdapter(IMemory ram, IBaseDevice io) {
        super(ram, io);
    }

    @Override
    public int executeInstruction() {
        long startTStates = getTStates();
        executeOneInstruction();
        return (int) (getTStates() - startTStates);
    }

    @Override
    public int executeInstruction(int tStatesLimit) {
        int startTStates = (int) getTStates();
        long tillTStates = startTStates + tStatesLimit;
        while (getTStates() < tillTStates) {
            executeOneInstruction();
        }
        return (int) (getTStates() - startTStates);
    }

    @Override
    public boolean isBreakpoint(int address) {
        return false;
    }

    @Override
    public void setBreakpoint(int address, boolean state) {

    }

    @Override
    public void resetBreakpoints() {

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

}
