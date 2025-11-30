package spectrum.jfx.hardware.ula;

import lombok.Getter;
import lombok.Setter;
import spectrum.jfx.hardware.memory.Memory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UlaImpl implements Ula {

    private final Memory memory;

    private boolean interruptRequested = false;
    @Getter
    private final boolean ulaAddTStates = true;
    private final Map<Integer, Set<InPortListener>> inPortListeners = new HashMap<>();
    private final Map<Integer, Set<OutPortListener>> outPortListeners = new HashMap<>();
    private final ZXClock clock;

    public UlaImpl(Memory memory) {
        this.memory = memory;
        this.clock = new ZXClock();
    }

    @Override
    public void addPortListener(int port, InPortListener listener) {
        Set<InPortListener> listeners = inPortListeners.getOrDefault(port, new HashSet<>());
        listeners.add(listener);
        inPortListeners.put(port, listeners);
    }

    @Override
    public void addPortListener(int port, OutPortListener listener) {
        Set<OutPortListener> listeners = outPortListeners.getOrDefault(port, new HashSet<>());
        listeners.add(listener);
        outPortListeners.put(port, listeners);
    }

    @Override
    public void addClockListener(ClockListener listener) {
        clock.addClockListener(listener);
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
        if (ulaAddTStates) {
            clock.incrementTStates(4);
        }
        return memory.readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {
        if (ulaAddTStates) {
            clock.incrementTStates(3); // 3 clocks for read byte from RAM
        }
        return memory.readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {
        if (ulaAddTStates) {
            clock.incrementTStates(3);  // 3 clocks for write byte to RAM
        }
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
        int value = 0;
        if (inPortListeners.containsKey(port & 0xff)) {
            for (InPortListener listener : inPortListeners.get(port & 0xff)) {
                int portValue = listener.inPort(port) & 0xff;
                value = value | portValue;
            }
        }
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for read byte from bus
        }
        return value & 0xff;
    }

    @Override
    public void outPort(int port, int value) {
        port &= 0xff;
        if (outPortListeners.containsKey((port))) {
            for (OutPortListener listener : outPortListeners.get(port)) {
                listener.outPort(port, value);
            }
        }
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for write byte to bus
        }
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        // Additional clocks to be added on some instructions
        // Not to be changed, really.
        if (ulaAddTStates) {
            clock.incrementTStates(tstates);
            clock.addressOnBus(address, tstates);
        }
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        // Additional clocks to be added on INT & NMI
        // Not to be changed, really.
        if (ulaAddTStates) {
            clock.incrementTStates(tstates);
        }
    }

    @Override
    public boolean isActiveINT() {
        if (interruptRequested) {
            interruptRequested = false;
            return true;
        }
        return false;
    }

    @Override
    public void addTStates(int tStates) {
        // Adding external tStates, if CPU counts them
        clock.incrementTStates(tStates);
    }

    public void requestInterrupt() {
        interruptRequested = true;
    }

    @Override
    public long gettStates() {
        return clock.getTStates();
    }

    @Override
    public void reset() {
        clock.reset();
    }

}
