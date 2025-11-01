package spectrum.jfx.z80.ula;

import spectrum.jfx.z80.memory.Memory;
import spectrum.jfx.z80core.MemIoOps;

public class Ula implements MemIoOps {

    private final Memory memory;

    private long tStates = 0;
    private boolean interruptRequested = false;

    public Ula(Memory memory) {
        this.memory = memory;
    }

    @Override
    public void setRam(byte[] ram) {
        //z80Ram = ram;
    }

    @Override
    public void setPorts(byte[] ports) {
        //z80Ram = ports;
    }

    @Override
    public int fetchOpcode(int address) {
        // 3 clocks to fetch opcode from RAM and 1 execution clock
        tStates += 4;
        return memory.readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {
        tStates += 3; // 3 clocks for read byte from RAM
        return memory.readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {
        tStates += 3; // 3 clocks for write byte to RAM
        memory.writeByte(address, (byte) value);
    }

    @Override
    public int peek16(int address) {
        int lsb = peek8(address);
        int msb = peek8(address + 1);
        return (msb << 8) | lsb;
    }

    @Override
    public void poke16(int address, int word) {
        poke8(address, word);
        poke8(address + 1, word >>> 8);
    }

    @Override
    public int inPort(int port) {
        tStates += 4; // 4 clocks for read byte from bus
        return 255;
        //return ports[port] & 0xff;
    }

    @Override
    public void outPort(int port, int value) {
        tStates += 4; // 4 clocks for write byte to bus
        //ports[port] = (byte) value;
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        // Additional clocks to be added on some instructions
        // Not to be changed, really.
        this.tStates += tstates;
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        // Additional clocks to be added on INT & NMI
        // Not to be changed, really.
        this.tStates += tstates;
    }

    @Override
    public boolean isActiveINT() {
        if (interruptRequested) {
            interruptRequested = false;
            return true;
        }
        return false;
    }

    public void requestInterrupt() {
        interruptRequested = true;
    }

    @Override
    public long gettStates() {
        return tStates;
    }

    @Override
    public void reset() {
        tStates = 0;
    }

}
