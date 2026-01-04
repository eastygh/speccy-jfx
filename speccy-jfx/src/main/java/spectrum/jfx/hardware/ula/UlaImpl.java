package spectrum.jfx.hardware.ula;

import lombok.Getter;
import machine.MachineTypes;
import machine.SpectrumClock;
import org.apache.commons.lang3.NotImplementedException;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.memory.Memory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UlaImpl implements Ula {

    private final Memory memory;

    private volatile boolean interruptRequested = false;
    private volatile boolean interruptPending = false;
    private volatile long interruptPendingLength = 0;
    @Getter
    private final boolean ulaAddTStates;
    private final Map<Integer, Set<InPortListener>> inPortListeners = new HashMap<>();
    private final Map<Integer, Set<OutPortListener>> outPortListeners = new HashMap<>();
    private final ZXClock clock;
    private final MachineSettings machineSettings;
    // Support zx-core project lib
    private static final SpectrumClock spectrumClock = SpectrumClock.INSTANCE;

    private final byte[] contentionTable;


    public UlaImpl(Memory memory, MachineSettings machineSettings) {
        this.memory = memory;
        this.ulaAddTStates = machineSettings.isUlaAddTStates();
        this.machineSettings = machineSettings;
        this.contentionTable = new byte[machineSettings.getMachineType().tstatesFrame];
        if (machineSettings.getMachineType() == MachineTypes.SPECTRUM48K) {
            buildContentionTable48(machineSettings.getMachineType());
        } else {
            throw new NotImplementedException("Not implemented machine " + machineSettings.getMachineType());
        }
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
            if (memory.isScreenAddress(address)) {
                clock.incrementTStates(getContentionDelay(clock.getTStates()));
            }
        }
        return memory.readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {
        if (ulaAddTStates) {
            clock.incrementTStates(3); // 3 clocks for read byte from RAM
            if (memory.isScreenAddress(address)) {
                clock.incrementTStates(getContentionDelay(clock.getTStates()));
            }
        }
        return memory.readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {
        if (ulaAddTStates) {
            clock.incrementTStates(3);  // 3 clocks for write byte to RAM
            if (memory.isScreenAddress(address)) {
                clock.incrementTStates(getContentionDelay(clock.getTStates()));
            }
        }
        memory.writeByte(address, value);
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
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for read byte from bus
        }
        if (inPortListeners.containsKey(port & 0xff)) {
            for (InPortListener listener : inPortListeners.get(port & 0xff)) {
                int portValue = listener.inPort(port) & 0xff;
                value = value | portValue;
            }
        }
        return value & 0xff;
    }

    @Override
    public void outPort(int port, int value) {
        value &= 0xff;
        port &= 0xffff;
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for write byte to bus
        }
        int lowPort = port & 0xff;
        if (outPortListeners.containsKey(lowPort)) {
            for (OutPortListener listener : outPortListeners.get(lowPort)) {
                listener.outPort(port, value);
            }
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
            interruptPending = true;
            interruptPendingLength = clock.getTStates();
            return true;
        }
        if (interruptPending &&
                clock.getTStates() - interruptPendingLength > machineSettings.getMachineType().lengthINT
        ) {
            interruptPending = false;
        }
        return interruptPending;
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
        spectrumClock.reset();
    }

    private void buildContentionTable48(MachineTypes machineType) {

        for (int i = 0; i < machineType.tstatesFrame; i++) {
            contentionTable[i] = 0;
        }

        int currentT = machineType.firstScrByte;
        for (int line = 0; line < 192; line++) {

            for (int t = 0; t < 128; t += 8) {
                setDelay(currentT + t, 6);
                setDelay(currentT + t + 1, 5);
                setDelay(currentT + t + 2, 4);
                setDelay(currentT + t + 3, 3);
                setDelay(currentT + t + 4, 2);
                setDelay(currentT + t + 5, 1);

            }
            currentT += machineType.tstatesLine;
        }
    }

    private void setDelay(int tState, int delay) {
        if (tState < contentionTable.length) {
            contentionTable[tState] = (byte) delay;
        }
    }

    private int getContentionDelay(long currentTState) {
        return contentionTable[Math.toIntExact(currentTState % machineSettings.getMachineType().tstatesFrame)];
    }

    /***************************************
     * Z80Core Mem/IO adapter
     ***************************************/
    @Override
    public int IORead(int address) {
        return inPort(address);
    }

    @Override
    public void IOWrite(int address, int data) {
        outPort(address, data);
    }

    @Override
    public int readByte(int address) {
        return memory.readByte(address);
    }

    @Override
    public int readWord(int address) {
        return memory.readWord(address);
    }

    @Override
    public void writeByte(int address, int data) {
        memory.writeByte(address, data);
    }

    @Override
    public void writeWord(int address, int data) {
        memory.writeWord(address, data);
    }
}
