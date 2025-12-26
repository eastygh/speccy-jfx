package spectrum.jfx.z80core;

/**
 *
 * @author jsanchez
 */
public class Z80State {
    // Accumulator and other 8-bit registers
    private int regA, regB, regC, regD, regE, regH, regL;
    // Flags SIGN, ZERO, 5, HALFCARRY, 3, PARITY and ADDSUB (n), carryFlag
    private int regF;
    // The last instruction modified the flags
    private boolean flagQ;
    // Alternate accumulator and flags -- 8 bits
    private int regAx;
    private int regFx;
    // Alternate registers
    private int regBx, regCx, regDx, regEx, regHx, regLx;
    // Special purpose registers
    // *PC -- Program Counter -- 16 bits*
    private int regPC;
    // *IX -- Index register -- 16 bits*
    private int regIX;
    // *IY -- Index register -- 16 bits*
    private int regIY;
    // *SP -- Stack Pointer -- 16 bits*
    private int regSP;
    // *I -- Interrupt vector -- 8 bits*
    private int regI;
    // *R -- Memory refresh -- 7 bits*
    private int regR;
    // Interrupt flip-flops
    private boolean ffIFF1 = false;
    private boolean ffIFF2 = false;
    // EI only enables interrupts AFTER executing the
    // next instruction (except if the next instruction is an EI...)
    private boolean pendingEI = false;
    // NMI line status
    private boolean activeNMI = false;
    // Whether the INT line is active
    // On the 48 the INT line is active for 32 clock cycles
    // On the 128 and higher, it's active for 36 clock cycles
    private boolean activeINT = false;
    // Interrupt modes
    private Z80.IntMode modeINT = Z80.IntMode.IM0;
    // halted == true when the CPU is executing a HALT (28/03/2010)
    private boolean halted = false;
    /**
     * Internal register that the CPU uses as follows
     * <p>
     * ADD HL,xx      = H register value before the addition
     * LD r,(IX/IY+d) = Upper byte of the IX/IY+d sum
     * JR d           = Upper byte of the jump destination address
     * <p>
     * 04/12/2008     Don't leave yet, there's more. With what has been
     * implemented so far it seems to work. The rest of
     * the story is told in:
     * http://zx.pk.ru/attachment.php?attachmentid=2989
     * <p>
     * 25/09/2009     MEMPTR emulation has been completed. Note that
     * it's not possible to check if MEMPTR has been emulated well until
     * the register behavior in CPI and CPD instructions is emulated.
     * Without this, all z80tests.tap tests will fail even if the
     * register has been emulated well in ALL other instructions.
     * Shit yourself, little parrot.
     */
    private int memptr;

    public Z80State() {
    }

    // Access to 8-bit registers
    public final int getRegA() {
        return regA;
    }

    public final void setRegA(int value) {
        regA = value & 0xff;
    }

    public final int getRegF() {
        return regF;
    }

    public final void setRegF(int value) {
        regF = value & 0xff;
    }

    public final int getRegB() {
        return regB;
    }

    public final void setRegB(int value) {
        regB = value & 0xff;
    }

    public final int getRegC() {
        return regC;
    }

    public final void setRegC(int value) {
        regC = value & 0xff;
    }

    public final int getRegD() {
        return regD;
    }

    public final void setRegD(int value) {
        regD = value & 0xff;
    }

    public final int getRegE() {
        return regE;
    }

    public final void setRegE(int value) {
        regE = value & 0xff;
    }

    public final int getRegH() {
        return regH;
    }

    public final void setRegH(int value) {
        regH = value & 0xff;
    }

    public final int getRegL() {
        return regL;
    }

    public final void setRegL(int value) {
        regL = value & 0xff;
    }

    // Access to alternate 8-bit registers
    public final int getRegAx() {
        return regAx;
    }

    public final void setRegAx(int value) {
        regAx = value & 0xff;
    }

    public final int getRegFx() {
        return regFx;
    }

    public final void setRegFx(int value) {
        regFx = value & 0xff;
    }

    public final int getRegBx() {
        return regBx;
    }

    public final void setRegBx(int value) {
        regBx = value & 0xff;
    }

    public final int getRegCx() {
        return regCx;
    }

    public final void setRegCx(int value) {
        regCx = value & 0xff;
    }

    public final int getRegDx() {
        return regDx;
    }

    public final void setRegDx(int value) {
        regDx = value & 0xff;
    }

    public final int getRegEx() {
        return regEx;
    }

    public final void setRegEx(int value) {
        regEx = value & 0xff;
    }

    public final int getRegHx() {
        return regHx;
    }

    public final void setRegHx(int value) {
        regHx = value & 0xff;
    }

    public final int getRegLx() {
        return regLx;
    }

    public final void setRegLx(int value) {
        regLx = value & 0xff;
    }

    // Access to 16-bit registers
    public final int getRegAF() {
        return (regA << 8) | regF;
    }

    public final void setRegAF(int word) {
        regA = (word >>> 8) & 0xff;

        regF = word & 0xff;
    }

    public final int getRegAFx() {
        return (regAx << 8) | regFx;
    }

    public final void setRegAFx(int word) {
        regAx = (word >>> 8) & 0xff;
        regFx = word & 0xff;
    }

    public final int getRegBC() {
        return (regB << 8) | regC;
    }

    public final void setRegBC(int word) {
        regB = (word >>> 8) & 0xff;
        regC = word & 0xff;
    }

    public final int getRegBCx() {
        return (regBx << 8) | regCx;
    }

    public final void setRegBCx(int word) {
        regBx = (word >>> 8) & 0xff;
        regCx = word & 0xff;
    }

    public final int getRegDE() {
        return (regD << 8) | regE;
    }

    public final void setRegDE(int word) {
        regD = (word >>> 8) & 0xff;
        regE = word & 0xff;
    }

    public final int getRegDEx() {
        return (regDx << 8) | regEx;
    }

    public final void setRegDEx(int word) {
        regDx = (word >>> 8) & 0xff;
        regEx = word & 0xff;
    }

    public final int getRegHL() {
        return (regH << 8) | regL;
    }

    public final void setRegHL(int word) {
        regH = (word >>> 8) & 0xff;
        regL = word & 0xff;
    }

    public final int getRegHLx() {
        return (regHx << 8) | regLx;
    }

    public final void setRegHLx(int word) {
        regHx = (word >>> 8) & 0xff;
        regLx = word & 0xff;
    }

    // Access to special purpose registers
    public final int getRegPC() {
        return regPC;
    }

    public final void setRegPC(int address) {
        regPC = address & 0xffff;
    }

    public final int getRegSP() {
        return regSP;
    }

    public final void setRegSP(int word) {
        regSP = word & 0xffff;
    }

    public final int getRegIX() {
        return regIX;
    }

    public final void setRegIX(int word) {
        regIX = word & 0xffff;
    }

    public final int getRegIY() {
        return regIY;
    }

    public final void setRegIY(int word) {
        regIY = word & 0xffff;
    }

    public final int getRegI() {
        return regI;
    }

    public final void setRegI(int value) {
        regI = value & 0xff;
    }

    public final int getRegR() {
        return regR;
    }

    public final void setRegR(int value) {
        regR = value & 0xff;
    }

    // Access to hidden MEMPTR register
    public final int getMemPtr() {
        return memptr;
    }

    public final void setMemPtr(int word) {
        memptr = word & 0xffff;
    }

    // Access to interrupt flip-flops
    public final boolean isIFF1() {
        return ffIFF1;
    }

    public final void setIFF1(boolean state) {
        ffIFF1 = state;
    }

    public final boolean isIFF2() {
        return ffIFF2;
    }

    public final void setIFF2(boolean state) {
        ffIFF2 = state;
    }

    public final boolean isNMI() {
        return activeNMI;
    }

    public final void setNMI(boolean nmi) {
        activeNMI = nmi;
    }

    // The NMI line is activated by impulse, not by level
    public final void triggerNMI() {
        activeNMI = true;
    }

    // The INT line is activated by level
    public final boolean isINTLine() {
        return activeINT;
    }

    public final void setINTLine(boolean intLine) {
        activeINT = intLine;
    }

    // Access to interrupt mode
    public final Z80.IntMode getIM() {
        return modeINT;
    }

    public final void setIM(Z80.IntMode mode) {
        modeINT = mode;
    }

    public final boolean isHalted() {
        return halted;
    }

    public void setHalted(boolean state) {
        halted = state;
    }

    public final boolean isPendingEI() {
        return pendingEI;
    }

    public final void setPendingEI(boolean state) {
        pendingEI = state;
    }

    /**
     * @return the flagQ
     */
    public boolean isFlagQ() {
        return flagQ;
    }

    /**
     * @param flagQ the flagQ to set
     */
    public void setFlagQ(boolean flagQ) {
        this.flagQ = flagQ;
    }
}
