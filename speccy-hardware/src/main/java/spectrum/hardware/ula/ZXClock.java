package spectrum.hardware.ula;

import lombok.Getter;
import machine.SpectrumClock;

import java.util.HashSet;
import java.util.Set;

public class ZXClock {

    @Getter
    private volatile long tStates = 0;
    private final Set<ClockListener> clockListeners = new HashSet<>();
    private static final SpectrumClock clock = SpectrumClock.INSTANCE;

    public void incrementTStates(int amount) {
        tStates += amount;
        clockListeners.forEach(listener -> listener.ticks(tStates, amount));
        clock.addTstates(amount);
    }

    public void addClockListener(ClockListener listener) {
        clockListeners.add(listener);
    }

    public void addressOnBus(int address, int tstates) {
        clockListeners.forEach(listener -> listener.addressOnBus(address, tstates));
    }

    public void removeClockListener(ClockListener listener) {
        clockListeners.remove(listener);
    }

    public void reset() {
        tStates = 0;
        clock.setTstates(0);
    }

}
