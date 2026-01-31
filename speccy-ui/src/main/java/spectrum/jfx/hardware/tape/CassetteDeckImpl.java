package spectrum.jfx.hardware.tape;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.tape.CassetteDeck;
import spectrum.hardware.sound.Sound;
import spectrum.hardware.tape.events.CassetteDeckEvent;
import spectrum.hardware.tape.events.TapFilePlaybackEvent;
import spectrum.hardware.tape.playback.PilotToneSignal;
import spectrum.hardware.tape.playback.SilentToneSignal;
import spectrum.hardware.tape.playback.TapFilePlayback;
import spectrum.hardware.tape.record.TapeRecordListener;
import spectrum.hardware.tape.record.TapeRecorder;
import spectrum.hardware.tape.tap.TapBlock;
import spectrum.hardware.ula.ClockListener;
import spectrum.hardware.ula.InPortListener;
import spectrum.hardware.ula.OutPortListener;
import spectrum.hardware.tape.model.TapeFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static spectrum.hardware.tape.TapeConstants.EAR_MASK;
import static spectrum.hardware.tape.TapeConstants.MIC_MASK;

/**
 * Implementation of the cassette deck for tape playback and recording.
 * <p>
 * Handles both:
 * - EAR (bit 6 of port 0xFE): Reading from tape
 * - MIC (bit 3 of port 0xFE): Writing to tape
 */
@Slf4j
public class CassetteDeckImpl
        implements InPortListener, OutPortListener, CassetteDeck, ClockListener, TapFilePlaybackEvent {

    private final List<CassetteDeckEvent> eventsReceivers = new CopyOnWriteArrayList<>();
    private final PilotToneSignal pilotToneSignal = new PilotToneSignal(spectrum.hardware.tape.TapeConstants.PILOT_PULSE, true);
    private final SilentToneSignal silentToneSignal = new SilentToneSignal();

    @Setter
    private Sound sound;
    private boolean pushBack = false;
    private boolean pushBackEnabled = true;

    private final AtomicReference<spectrum.hardware.tape.TapeSignal> tapeFilePlayback;
    private final TapeRecorder tapeRecorder;

    private volatile long tStates;

    public CassetteDeckImpl() {
        this.tapeFilePlayback = new AtomicReference<>(silentToneSignal);
        this.tapeRecorder = new TapeRecorder();
    }

    @Override
    public void addCassetteDeckEventListener(CassetteDeckEvent listener) {
        this.eventsReceivers.add(listener);
    }

    @Override
    public void addRecordListener(TapeRecordListener listener) {
        tapeRecorder.addListener(listener);
    }

    @Override
    public void removeRecordListener(TapeRecordListener listener) {
        tapeRecorder.removeListener(listener);
    }

    /**
     * Reads from port 0xFE - returns EAR level in bit 6.
     *
     * @param port Port address (0xFE)
     * @return D6 - 1 high, 0 low
     */
    @Override
    public int inPort(int port) {
        boolean ear = withTapeFile().earLevelAt(tStates);
        if (pushBack && sound != null && pushBackEnabled) {
            sound.pushBackTape(ear);
        }
        int value = ear ? EAR_MASK : 0;
        return value & 0xFF;
    }

    /**
     * Writes to port 0xFE - processes MIC level from bit 3.
     *
     * @param port  Port address (0xFE)
     * @param value Value written
     */
    @Override
    public void outPort(int port, int value) {
        if (tapeRecorder.isRecording()) {
            if (pushBack && sound != null && pushBackEnabled) {
                boolean mic = (value & MIC_MASK) != 0;
                //sound.pushBackTape(mic);
            }
            tapeRecorder.processOutput(value, tStates);
        }
    }

    @Override
    public void ticks(long tStates, int delta) {
        this.tStates = tStates;
    }

    // ========== Playback ==========

    @Override
    public void setMotor(boolean on) {
        withTapeFile().setMotor(on, tStates);
        pushBack = on;
        eventsReceivers.forEach(l -> l.onTapeMotorChanged(on));
    }

    @Override
    public void insertTape(TapeFile tape) {
        if (tape != null) {
            spectrum.hardware.tape.TapeSignal previous = tapeFilePlayback.get();
            if (previous != null) {
                previous.setMotor(false, tStates);
                pushBack = false;
            }
            tapeFilePlayback.set(new TapFilePlayback(true, tape, this));
            eventsReceivers.forEach(l -> l.onTapeChanged(tape));
            log.info("Tape inserted: {} sections", tape.getSections().size());
        }
    }

    @Override
    public void setSectionIndex(int index) {
        spectrum.hardware.tape.TapeSignal tapeSignal = tapeFilePlayback.get();
        if (tapeSignal != null) {
            tapeSignal.setSectionIndex(index);
        }
    }

    private spectrum.hardware.tape.TapeSignal withTapeFile() {
        spectrum.hardware.tape.TapeSignal tapeSignal = tapeFilePlayback.get();
        return tapeSignal == null ? pilotToneSignal : tapeSignal;
    }

    // ========== Recording ==========

    @Override
    public void startRecording() {
        if (tapeRecorder.isRecording()) {
            log.warn("Already recording");
            return;
        }
        pushBack = true;
        tapeRecorder.startRecording();
        log.info("Recording started");
    }

    @Override
    public void stopRecording() {
        if (!tapeRecorder.isRecording()) {
            return;
        }
        pushBack = false;
        tapeRecorder.stopRecording();
        log.info("Recording stopped, {} blocks", tapeRecorder.getBlocksRecorded());
    }

    @Override
    public boolean isRecording() {
        return tapeRecorder.isRecording();
    }

    @Override
    public int getRecordedBlockCount() {
        return tapeRecorder.getBlocksRecorded();
    }

    @Override
    public List<TapBlock> getRecordedBlocks() {
        return tapeRecorder.getRecordedBlocks();
    }

    @Override
    public void saveRecordedTape(String filePath) throws IOException {
        tapeRecorder.getFileWriter().writeToFile(filePath);
        log.info("Saved {} blocks to {}", tapeRecorder.getBlocksRecorded(), filePath);
    }

    @Override
    public void clearRecording() {
        tapeRecorder.reset();
        log.info("Recording cleared");
    }

    // ========== Playback Events ==========

    @Override
    public void onSectionChanged(int index, TapeFile tape) {
        eventsReceivers.forEach(listener -> listener.onTapeSectionChanged(index, tape));
    }

    @Override
    public void onTapeFinished(boolean success) {
        setMotor(false);
        eventsReceivers.forEach(listener -> listener.onTapeFinished(success));
    }

    // ========== Device Lifecycle ==========

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        setMotor(false);
        tapeFilePlayback.set(silentToneSignal);
        tStates = 0;
        if (tapeRecorder.isRecording()) {
            tapeRecorder.stopRecording();
        }
    }

    @Override
    public void open() {
        // ignored
    }

    @Override
    public void close() {
        reset();
    }

    @Override
    public void setSoundPushBack(boolean pushBack) {
        this.pushBackEnabled = pushBack;
    }
}
