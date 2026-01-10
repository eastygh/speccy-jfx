package spectrum.jfx.hardware.cpu;

public interface AddressHookListener {

    int call(int address, int opcode);

}
