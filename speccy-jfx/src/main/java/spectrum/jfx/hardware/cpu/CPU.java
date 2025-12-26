package spectrum.jfx.hardware.cpu;

public interface CPU {

    /**
     * Execute one instruction.
     * Returns the number of t-states executed.
     *
     * @return number of t-states executed
     */
    int executeInstruction();

    void reset();

    boolean isBreakpoint(int address);

    void setBreakpoint(int address, boolean state);

    void resetBreakpoints();

    void setRegA(int value);

    int getRegA();

    void setFlags(int value);

    int getFlags();

    void setRegPC(int address);

    void setRegSP(int address);

    int getRegIX();

    void setRegIX(int value);

    int getRegIY();

    void setRegIY(int value);

    int getRegDE();

    void setRegDE(int value);

    // Flags
    void setCarryFlag(boolean state);

    boolean isCarryFlag();

}
