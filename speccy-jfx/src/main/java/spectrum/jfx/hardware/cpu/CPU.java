package spectrum.jfx.hardware.cpu;

public interface CPU {

    /**
     * Execute one instruction.
     * Returns the number of t-states executed.
     *
     * @return number of t-states executed
     */
    int executeInstruction();

    /**
     * Execute instructions until t-states limit is reached.
     * Returns the number of t-states executed.
     *
     * @param tStatesLimit maximum number of t-states to execute
     * @return number of t-states executed
     */
    int executeInstruction(int tStatesLimit);

    void reset();

    boolean isBreakpoint(int address);

    void setBreakpoint(int address, boolean state);

    void resetBreakpoints();

    void setRegA(int value);

    int getRegA();

    void setFlags(int value);

    int getFlags();

    void setRegPC(int address);

    int getRegSP();

    void setRegSP(int address);

    int getRegIX();

    void setRegIX(int value);

    int getRegIY();

    void setRegIY(int value);

    int getRegDE();

    void setRegDE(int value);

    int getRegHL();

    void setRegBC(int value);

    int getRegBC();

    void setRegHL(int value);

    // Flags
    void setCarryFlag(boolean state);

    boolean isCarryFlag();

    int getRegPC();

}
