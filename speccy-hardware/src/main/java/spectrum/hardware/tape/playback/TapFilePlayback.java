package spectrum.hardware.tape.playback;

import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.tape.TapeSignal;
import spectrum.hardware.tape.events.TapFilePlaybackEvent;
import spectrum.hardware.tape.model.TapeFile;
import spectrum.hardware.tape.model.TapeSection;

import static spectrum.hardware.tape.TapeConstants.*;
import static spectrum.hardware.tape.model.TapeSection.SectionType.HEADER;
import static spectrum.hardware.tape.model.TapeSection.SectionType.PROGRAM;

/**
 * Plays back a TAP file by generating appropriate pulse signals.
 * Implements the ZX Spectrum tape protocol for loading.
 */
@Slf4j
public class TapFilePlayback implements TapeSignal {

    private final TapeFile tape;
    private final boolean initialLevel;
    private final TapeSignal silence = new SilentToneSignal();

    private volatile long currentTStates = 0;
    private volatile long currentPulseLength = PILOT_PULSE;
    private volatile long pilotPulseCount = PILOT_HEADER_COUNT_ACTUAL;
    private volatile int pulseStage = 0;
    private volatile boolean currentLevel;
    private volatile boolean motorOn = false;

    private volatile int dataPosition = 0;
    private volatile int currentByte = 0;
    private volatile int bitPosition = 7;

    private volatile TapePlaybackState tapeState = TapePlaybackState.PILOT;
    private byte[] data;
    private int playBackSectionIndex = 0;

    private final TapFilePlaybackEvent listener;

    public TapFilePlayback(boolean initialLevel, TapeFile tape, TapFilePlaybackEvent listener) {
        this.initialLevel = initialLevel;
        this.tape = tape;
        this.listener = listener;
        reset(0);
    }

    @Override
    public void setMotor(boolean on, long startTStates) {
        if (motorOn == on) {
            return;
        }
        if (on) {
            preparePlayBackSection();
            reset(startTStates);
        }
        motorOn = on;
    }

    @Override
    public boolean earLevelAt(long tstates) {
        if (!motorOn) {
            return silence.earLevelAt(tstates);
        }
        if (tstates < (currentTStates + currentPulseLength)) {
            return currentLevel;
        }
        currentLevel = !currentLevel;
        currentTStates = tstates;

        if (!switchToNextSection()) {
            return silence.earLevelAt(tstates);
        }

        if (tapeState == TapePlaybackState.DATA || tapeState == TapePlaybackState.PILOT) {
            if (pulseStage == 0) {
                currentPulseLength = getNextPulseLength();
                pulseStage = 1;
            } else {
                pulseStage = 0;
            }
        } else {
            currentPulseLength = getNextPulseLength();
        }

        return currentLevel;
    }

    private long getNextPulseLength() {
        if (tapeState == TapePlaybackState.PAUSE) {
            tapeState = TapePlaybackState.PILOT;
            currentLevel = initialLevel;
            return PAUSE_DURATION;
        }
        if (tapeState == TapePlaybackState.PILOT) {
            if (pilotPulseCount > 0) {
                pilotPulseCount--;
                return PILOT_PULSE;
            }
            tapeState = TapePlaybackState.SYNC1;
            return SYNC1_PULSE;
        }
        if (tapeState == TapePlaybackState.SYNC1) {
            tapeState = TapePlaybackState.SYNC2;
            return SYNC2_PULSE;
        }
        if (tapeState == TapePlaybackState.SYNC2) {
            tapeState = TapePlaybackState.DATA;
            bitPosition = 7;
            dataPosition = 0;
        }
        if (tapeState == TapePlaybackState.DATA && dataPosition < data.length) {
            return getNextBit() == 0 ? ZERO_PULSE : ONE_PULSE;
        } else {
            if (tapeState == TapePlaybackState.DATA) {
                tapeState = TapePlaybackState.FINAL_SYNC;
            }
        }
        if (tapeState == TapePlaybackState.FINAL_SYNC) {
            return FINAL_SYNC_PULSE;
        }
        return 0;
    }

    private int getNextBit() {
        if (bitPosition == 7) {
            currentByte = data[dataPosition] & 0xFF;
        }
        int bit = (currentByte >> bitPosition) & 1;
        bitPosition--;
        if (bitPosition < 0) {
            bitPosition = 7;
            dataPosition++;
        }
        return bit;
    }

    private boolean switchToNextSection() {
        if (tapeState != TapePlaybackState.FINAL_SYNC) {
            return motorOn;
        }
        playBackSectionIndex++;
        if (playBackSectionIndex >= tape.getSections().size()) {
            playBackSectionIndex = 0;
            motorOn = false;
            listener.onTapeFinished(true);
        }
        reset(currentTStates);
        preparePlayBackSection();
        return motorOn;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void setSectionIndex(int index) {
        this.playBackSectionIndex = index;
    }

    private void reset(long tstates) {
        TapeSection section = tape.getSections().get(playBackSectionIndex);
        this.currentTStates = tstates;
        this.currentPulseLength = PILOT_PULSE;
        this.currentLevel = initialLevel;
        this.pilotPulseCount = (section.getType() == HEADER || section.getType() == PROGRAM)
                ? PILOT_HEADER_COUNT_ACTUAL
                : PILOT_DATA_COUNT;
        this.tapeState = TapePlaybackState.PAUSE;
        this.dataPosition = 0;
        this.currentByte = 0;
        this.bitPosition = 7;
    }

    private void preparePlayBackSection() {
        this.data = tape.getSections().get(playBackSectionIndex).getData();
        listener.onSectionChanged(playBackSectionIndex, tape);
    }
}
