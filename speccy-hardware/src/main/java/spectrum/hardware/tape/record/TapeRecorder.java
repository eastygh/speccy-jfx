package spectrum.hardware.tape.record;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.tape.tap.TapBlock;
import spectrum.hardware.tape.tap.TapBlockBuilder;
import spectrum.hardware.tape.tap.TapFileWriter;

import java.util.ArrayList;
import java.util.List;

import static spectrum.hardware.tape.TapeConstants.*;


/**
 * State machine for recording tape data from MIC output.
 * <p>
 * Decodes the standard ZX Spectrum tape protocol:
 * IDLE → (pilot pulses) → PILOT → (sync1) → SYNC1 → (sync2) → SYNC2 → DATA
 * → (block complete) → BLOCK_COMPLETE → (pilot) → PILOT
 */
@Slf4j
public class TapeRecorder implements PulseDetector.PulseListener, ByteAccumulator.ByteListener {

    @Getter
    private RecordingState state = RecordingState.IDLE;
    @Getter
    private boolean recording = false;

    private final List<TapeRecordListener> listeners = new ArrayList<>();
    private final PulseDetector pulseDetector;
    private final ByteAccumulator byteAccumulator;
    private final TapBlockBuilder blockBuilder;
    /**
     * -- GETTER --
     * Gets the file writer containing recorded blocks.
     *
     */
    @Getter
    private final TapFileWriter fileWriter;

    private int pilotPulseCount = 0;
    private int byteIndex = 0;
    /**
     * -- GETTER --
     * Gets the number of blocks recorded.
     *
     */
    @Getter
    private int blocksRecorded = 0;

    /**
     * Creates a new tape recorder.
     */
    public TapeRecorder() {
        this.pulseDetector = new PulseDetector(this);
        this.byteAccumulator = new ByteAccumulator(this);
        this.blockBuilder = new TapBlockBuilder();
        this.fileWriter = new TapFileWriter();
    }

    /**
     * Adds a listener for recording events.
     *
     * @param listener Listener to add
     */
    public void addListener(TapeRecordListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a recording listener.
     *
     * @param listener Listener to remove
     */
    public void removeListener(TapeRecordListener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts recording.
     */
    public void startRecording() {
        recording = true;
        reset();
        log.info("Recording started");
    }

    /**
     * Stops recording.
     */
    public void stopRecording() {
        recording = false;

        // If we have partial block data, try to complete it
        if (state == RecordingState.DATA && blockBuilder.getByteCount() > 0) {
            completeBlock();
        }

        listeners.forEach(l -> l.onRecordingStopped(blocksRecorded));
        log.info("Recording stopped, {} blocks recorded", blocksRecorded);
    }

    /**
     * Processes an output port write.
     *
     * @param value   Value written to port 0xFE
     * @param tStates Current T-state counter
     */
    public void processOutput(int value, long tStates) {
        if (!recording) {
            return;
        }
        pulseDetector.processOutput(value, tStates);
    }

    /**
     * Called by PulseDetector when a complete pulse is detected.
     */
    @Override
    public void onPulseDetected(int duration, boolean level) {
        if (!recording) {
            return;
        }

        switch (state) {
            case IDLE -> handleIdlePulse(duration);
            case PILOT -> handlePilotPulse(duration);
            case SYNC1 -> handleSync1Pulse(duration);
            case SYNC2 -> handleSync2Pulse(duration);
            case DATA -> handleDataPulse(duration);
            case BLOCK_COMPLETE -> handleBlockCompletePulse(duration);
            case ERROR -> handleErrorPulse(duration);
        }
    }

    /**
     * Called by ByteAccumulator when a complete byte is ready.
     */
    @Override
    public void onByteComplete(int value) {
        blockBuilder.addByte(value);
        listeners.forEach(l -> l.onByteRecorded(value, byteIndex));
        byteIndex++;

        log.trace("Byte {} recorded: 0x{}", byteIndex, Integer.toHexString(value));
    }

    private void handleIdlePulse(int duration) {
        if (isPilotPulse(duration)) {
            pilotPulseCount = 1;
            state = RecordingState.PILOT;
            log.debug("Pilot tone detected");
        }
    }

    private void handlePilotPulse(int duration) {
        if (isPilotPulse(duration)) {
            pilotPulseCount++;
        } else if (pilotPulseCount >= MIN_PILOT_PULSES && isSync1Pulse(duration)) {
            state = RecordingState.SYNC1;
            listeners.forEach(l -> l.onPilotDetected(pilotPulseCount));
            log.debug("Pilot complete ({} pulses), sync1 detected", pilotPulseCount);
        } else {
            // Invalid pulse - reset to looking for pilot
            log.debug("Invalid pulse during pilot: {} T-states after {} pulses", duration, pilotPulseCount);
            if (isPilotPulse(duration)) {
                pilotPulseCount = 1;
            } else {
                resetToIdle();
            }
        }
    }

    private void handleSync1Pulse(int duration) {
        if (isSync2Pulse(duration)) {
            state = RecordingState.SYNC2;
            log.debug("Sync2 detected");
        } else {
            notifyError("Expected sync2 pulse, got " + duration + " T-states");
            resetToIdle();
        }
    }

    private void handleSync2Pulse(int duration) {
        // First data pulse
        state = RecordingState.DATA;
        byteAccumulator.reset();
        blockBuilder.reset();
        byteIndex = 0;
        handleDataPulse(duration);
        log.debug("Data phase started");
    }

    private void handleDataPulse(int duration) {
        if (isPilotPulse(duration)) {
            // New pilot tone - end of current block
            completeBlock();
            pilotPulseCount = 1;
            state = RecordingState.PILOT;
            return;
        }

        int bit = classifyDataPulse(duration);
        if (bit < 0) {
            // Invalid pulse - might be end of block or error
            if (blockBuilder.getByteCount() > 0) {
                completeBlock();
                state = RecordingState.BLOCK_COMPLETE;
            } else {
                notifyError("Invalid data pulse: " + duration + " T-states");
                state = RecordingState.ERROR;
            }
            return;
        }

        byteAccumulator.addBit(bit);
    }

    private void handleBlockCompletePulse(int duration) {
        // Looking for next pilot tone
        if (isPilotPulse(duration)) {
            pilotPulseCount = 1;
            state = RecordingState.PILOT;
        }
    }

    private void handleErrorPulse(int duration) {
        // Try to recover by looking for pilot
        if (isPilotPulse(duration)) {
            pilotPulseCount = 1;
            state = RecordingState.PILOT;
            log.debug("Recovered from error, pilot detected");
        }
    }

    private void completeBlock() {
        if (blockBuilder.getByteCount() == 0) {
            return;
        }

        TapBlock block = blockBuilder.build();
        fileWriter.addBlock(block);
        blocksRecorded++;

        listeners.forEach(l -> l.onBlockRecorded(
                block.getFlagByte(),
                block.getData(),
                block.isChecksumValid()
        ));

        log.info("Block recorded: {}", block);
        blockBuilder.reset();
    }

    private boolean isPilotPulse(int duration) {
        return duration >= PILOT_MIN && duration <= PILOT_MAX;
    }

    private boolean isSync1Pulse(int duration) {
        return duration >= SYNC1_MIN && duration <= SYNC1_MAX;
    }

    private boolean isSync2Pulse(int duration) {
        return duration >= SYNC2_MIN && duration <= SYNC2_MAX;
    }

    private int classifyDataPulse(int duration) {
        if (duration >= ZERO_MIN && duration <= ZERO_MAX) {
            return 0;
        }
        if (duration >= ONE_MIN && duration <= ONE_MAX) {
            return 1;
        }
        return -1;  // Invalid
    }

    private void resetToIdle() {
        state = RecordingState.IDLE;
        pilotPulseCount = 0;
        byteAccumulator.reset();
    }

    private void notifyError(String message) {
        listeners.forEach(l -> l.onRecordingError(state, message));
        log.warn("Recording error in state {}: {}", state, message);
    }

    /**
     * Resets the recorder state.
     */
    public void reset() {
        state = RecordingState.IDLE;
        pilotPulseCount = 0;
        byteIndex = 0;
        blocksRecorded = 0;
        pulseDetector.reset();
        byteAccumulator.reset();
        blockBuilder.reset();
        fileWriter.clear();
    }

    /**
     * Gets all recorded blocks.
     *
     * @return List of recorded TapBlocks
     */
    public List<TapBlock> getRecordedBlocks() {
        return fileWriter.getBlocks();
    }

}
