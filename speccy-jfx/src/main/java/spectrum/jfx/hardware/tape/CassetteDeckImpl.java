package spectrum.jfx.hardware.tape;

import lombok.Setter;
import spectrum.jfx.hardware.sound.Sound;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.model.TapeFile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class CassetteDeckImpl
        implements InPortListener, OutPortListener, CassetteDeck, ClockListener, TapFilePlaybackEvent {

    private final List<CassetteDeckEvent> eventsReceivers = new CopyOnWriteArrayList<>();
    private final PilotToneSignal pilotToneSignal = new PilotToneSignal(2168, true);
    private final SilentToneSignal silentToneSignal = new SilentToneSignal();

    @Setter
    private Sound sound;
    private boolean pushBack = false;

    private AtomicReference<TapeSignal> tapeFilePlayback;

    private volatile long tStates;

    public CassetteDeckImpl() {
        this.tapeFilePlayback = new AtomicReference<>(silentToneSignal);
    }

    @Override
    public void addCassetteDeckEventListener(CassetteDeckEvent listener) {
        this.eventsReceivers.add(listener);
    }

    /**
     *
     * @param port - 0xFE
     * @return D6 - 1 high, 0 low
     */
    @Override
    public int inPort(int port) {
        boolean ear = withTapeFile().earLevelAt(tStates);
        if (pushBack && sound != null) {
            sound.pushBackTape(ear);
        }
        int value = ear ? 0b0100_0000 : 0b0000_0000;
        return value & 0xff;
    }

    @Override
    public void outPort(int port, int value) {

    }

    @Override
    public void ticks(long tStates, int delta) {
        this.tStates = tStates;
    }

    @Override
    public void setMotor(boolean on) {
        withTapeFile().setMotor(on, tStates);
        pushBack = on;
    }

    @Override
    public void insertTape(TapeFile tape) {
        if (tape != null) {
            TapeSignal previous = tapeFilePlayback.get();
            if (previous != null) {
                previous.setMotor(false, tStates);
                pushBack = false;
            }
            tapeFilePlayback.set(new TapFilePlayback(true, tape, this));
        }
    }

    @Override
    public void setSectionIndex(int index) {
        TapeSignal tapeSignal = tapeFilePlayback.get();
        if (tapeSignal != null) {
            tapeSignal.setSectionIndex(index);
        }
    }

    private TapeSignal withTapeFile() {
        TapeSignal tapeSignal = tapeFilePlayback.get();
        return tapeSignal == null ? pilotToneSignal : tapeSignal;
    }

    @Override
    public void onSectionChanged(int index, TapeFile tape) {
        eventsReceivers.forEach(listener -> listener.onTapeSectionChanged(index, tape));
    }

    @Override
    public void onTapeFinished(boolean success) {
        setMotor(false);
        eventsReceivers.forEach(listener -> listener.onTapeFinished(success));
    }

}
