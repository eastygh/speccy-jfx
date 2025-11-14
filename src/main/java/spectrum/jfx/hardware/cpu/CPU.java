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

}
