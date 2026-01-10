package spectrum.jfx.hardware.ula;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;
import machine.SpectrumClock;
import org.apache.commons.lang3.NotImplementedException;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.memory.Memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class UlaImpl implements Ula {

    @Getter
    private final Memory memory;

    private volatile boolean interruptRequested = false;
    private volatile boolean interruptPending = false;
    private volatile long interruptPendingLength = 0;
    @Getter
    private final boolean ulaAddTStates;
    @SuppressWarnings("unchecked")
    private final Set<InPortListener>[] inPortListeners = new Set[256];
    @SuppressWarnings("unchecked")
    private final Set<OutPortListener>[] outPortListeners = new Set[256];
    private final List<AddressHookController> addressHookControllers = new ArrayList<>();
    private final ZXClock clock;
    private final MachineSettings machineSettings;
    // Floating bus by ULA (IN ports)
    private final FloatingBus floatingBus;
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
        } else if (machineSettings.getMachineType() == MachineTypes.SPECTRUM128K) {
            buildContentionTable128(machineSettings.getMachineType());
        } else {
            throw new NotImplementedException("Not implemented machine " + machineSettings.getMachineType());
        }
        this.floatingBus = new FloatingBus(machineSettings);
        this.clock = new ZXClock();
    }

    @Override
    public void addPortListener(int port, InPortListener listener) {
        int portLow = port & 0xff;
        Set<InPortListener> listeners = inPortListeners[portLow];
        if (listeners == null) {
            listeners = new HashSet<>();
        }
        listeners.add(listener);
        inPortListeners[portLow] = listeners;
    }

    @Override
    public void addPortListener(int port, OutPortListener listener) {
        int portLow = port & 0xff;
        Set<OutPortListener> listeners = outPortListeners[portLow];
        if (listeners == null) {
            listeners = new HashSet<>();
        }
        listeners.add(listener);
        outPortListeners[portLow] = listeners;
    }

    @Override
    public void addClockListener(ClockListener listener) {
        clock.addClockListener(listener);
    }

    @Override
    public void addAddressHookController(AddressHookController controller) {
        addressHookControllers.add(controller);
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
        if (!addressHookControllers.isEmpty()) {
            // Any address hooks (like TR-DOS)
            for (AddressHookController controller : addressHookControllers) {
                controller.checkAddress(address);
            }
        }
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
        int portLow = port & 0xff;
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for read byte from the bus
        }
        if (inPortListeners[portLow] != null) {
            Set<InPortListener> listeners = inPortListeners[portLow];
            for (InPortListener listener : listeners) {
                int portValue = listener.inPort(port) & 0xff;
                if (listener.isExclusiveValue(port)) {
                    return portValue;
                }
                if (!listener.isIgnoreValue(port)) {
                    value = value | portValue;
                }
            }
        } else {
            value = floatingBus.inPort(port);
        }
        return value & 0xff;
    }

    @Override
    public void outPort(int port, int value) {
        value &= 0xff;
        port &= 0xffff;
        if (ulaAddTStates) {
            clock.incrementTStates(4); // 4 clocks for writing byte to bus
        }
        int lowPort = port & 0xff;
        if (outPortListeners[lowPort] != null) {
            for (OutPortListener listener : outPortListeners[lowPort]) {
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

    private void buildContentionTable128(MachineTypes machineType) {

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
                setDelay(currentT + t + 6, 0); // Эти два такта не тормозят CPU
                setDelay(currentT + t + 7, 0);
            }
            currentT += machineType.tstatesLine; // 228 для 128K
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
