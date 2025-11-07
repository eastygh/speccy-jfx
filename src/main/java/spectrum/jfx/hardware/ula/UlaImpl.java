package spectrum.jfx.hardware.ula;

import spectrum.jfx.hardware.memory.Memory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UlaImpl implements Ula {

    private final Memory memory;

    private long tStates = 0;
    private boolean interruptRequested = false;
    private final Map<Byte, Set<InPortListener>> inPortListeners = new HashMap<>();
    private final Map<Byte, Set<OutPortListener>> outPortListeners = new HashMap<>();

    public UlaImpl(Memory memory) {
        this.memory = memory;
    }

    @Override
    public void addPortListener(byte port, InPortListener listener) {
        Set<InPortListener> listeners = inPortListeners.getOrDefault(port, new HashSet<>());
        listeners.add(listener);
        inPortListeners.put(port, listeners);
    }

    @Override
    public void addPortListener(byte port, OutPortListener listener) {
        Set<OutPortListener> listeners = outPortListeners.getOrDefault(port, new HashSet<>());
        listeners.add(listener);
        outPortListeners.put(port, listeners);
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
        int value = 0xff;
        if (inPortListeners.containsKey((byte) port)) {
            for (InPortListener listener : inPortListeners.get((byte) port)) {
                value = value & listener.inPort(port);
            }
        }
        return value;
    }

    @Override
    public void outPort(int port, int value) {
        tStates += 4; // 4 clocks for write byte to bus
        if (outPortListeners.containsKey((byte) port)) {
            for (OutPortListener listener : outPortListeners.get((byte) port)) {
                listener.outPort(port, (byte) value);
            }
        }
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
