package spectrum.jfx.hardware.cpu;

public interface BreakPointListener {

    int call(int address, int opcode);

}
