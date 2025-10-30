package spectrum.jfx.z80core;

public interface MemIoOps {

    void setRam(byte[] ram);

    void setPorts(byte[] ports);

    int fetchOpcode(int address);

    int peek8(int address);

    void poke8(int address, int value);

    int peek16(int address);

    void poke16(int address, int word);

    int inPort(int port);

    void outPort(int port, int value);

    void addressOnBus(int address, int tstates);

    void interruptHandlingTime(int tstates);

    boolean isActiveINT();

    long gettStates();

    void reset();

}
