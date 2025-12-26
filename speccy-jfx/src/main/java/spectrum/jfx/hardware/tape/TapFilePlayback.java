package spectrum.jfx.hardware.tape;

import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.model.TapeFile;
import spectrum.jfx.model.TapeSection;

import static spectrum.jfx.model.TapeSection.SectionType.HEADER;
import static spectrum.jfx.model.TapeSection.SectionType.PROGRAM;

@Slf4j
public class TapFilePlayback implements TapeSignal {

    private static final int PILOT_PULSE_LENGTH = 2168;  // Pause impulse length
    private static final int PILOT_PULSE_DATA_COUNT = 3223;    // Pilot impulse count for data
    private static final int PILOT_PULSE_HEADER_COUNT = 4123;    // Pilot impulse count for header
    private static final int SYNC1_PULSE_LENGTH = 667;   // First sync impulse length
    private static final int SYNC2_PULSE_LENGTH = 735;   // Second sync impulse length
    private static final int ZERO_PULSE_LENGTH = 855;    // Impulse for bit 0
    private static final int ONE_PULSE_LENGTH = 1710;    // Impulse for bit 1
    private static final int FINAL_SYNC = 945; // final sync impulse
    private static final int PAUSE_PULSE_LENGTH = 350000;    // Pause between sections

    private final TapeFile tape;

    private final boolean initialLevel;
    private final TapeSignal silence = new SilentToneSignal();

    private volatile long currentTStates = 0;
    private volatile long currentPulseLength = PILOT_PULSE_LENGTH;
    private volatile long pilotPulseCount = PILOT_PULSE_HEADER_COUNT;
    private volatile int pulseStage = 0;
    private volatile boolean currentLevel;
    private volatile boolean motorOn = false;

    private volatile int dataPosition = 0;
    private volatile int currentByte = 0;
    private volatile int bitPosition = 7;

    private volatile TapeState tapeState = TapeState.PILOT;
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

        if (tapeState == TapeState.DATA || tapeState == TapeState.PILOT) {
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
        if (tapeState == TapeState.PAUSE) {
            tapeState = TapeState.PILOT;
            currentLevel = initialLevel;
            return PAUSE_PULSE_LENGTH;
        }
        if (tapeState == TapeState.PILOT) {
            if (pilotPulseCount > 0) {
                pilotPulseCount--;
                return PILOT_PULSE_LENGTH;
            }
            tapeState = TapeState.SYNC1;
            return SYNC1_PULSE_LENGTH;
        }
        if (tapeState == TapeState.SYNC1) {
            tapeState = TapeState.SYNC2;
            return SYNC2_PULSE_LENGTH;
        }
        if (tapeState == TapeState.SYNC2) {
            tapeState = TapeState.DATA;
            bitPosition = 7;
            dataPosition = 0;
        }
        if (tapeState == TapeState.DATA && dataPosition < data.length) {
            return getNextBit() == 0 ? ZERO_PULSE_LENGTH : ONE_PULSE_LENGTH;
        } else {
            if (tapeState == TapeState.DATA) {
                tapeState = TapeState.FINAL_SYNC;
            }
        }
        if (tapeState == TapeState.FINAL_SYNC) {
            return FINAL_SYNC;
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
        if (tapeState != TapeState.FINAL_SYNC) {
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
        this.currentPulseLength = PILOT_PULSE_LENGTH;
        this.currentLevel = initialLevel;
        this.pilotPulseCount = (section.getType() == HEADER || section.getType() == PROGRAM) ? PILOT_PULSE_HEADER_COUNT : PILOT_PULSE_DATA_COUNT;
        this.tapeState = TapeState.PAUSE;
        this.dataPosition = 0;
        this.currentByte = 0;
        this.bitPosition = 7;
    }

    private void preparePlayBackSection() {
        this.data = tape.getSections().get(playBackSectionIndex).getData();
        listener.onSectionChanged(playBackSectionIndex, tape);
    }

    public enum TapeState {
        PAUSE,
        PILOT,
        SYNC1,
        SYNC2,
        DATA,
        FINAL_SYNC
    }

}
