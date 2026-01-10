package spectrum.jfx.hardware.disk;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.machine.MachineSettings;

import java.util.Arrays;

@Slf4j
public class WD1793 implements DiskController {

    private int statusReg;
    private int commandReg;
    private int trackReg;
    private int sectorReg;
    private int dataReg;

    @Getter
    private int systemReg;

    private int currentDrive;
    private int currentSide;
    private boolean motorOn;

    private boolean busy;
    private boolean drq;
    private boolean intrq;

    private long stateTicks;
    private final double tStatesPerMs;

    private final byte[] sectorBuffer = new byte[256];
    private int bufferPtr;
    private boolean isReading;
    private boolean isWriting;

    private final byte[][] drives = new byte[4][640 * 1024];
    private final boolean[] diskInserted = new boolean[4];
    private final boolean[] lastLedStates = new boolean[4];

    private volatile boolean activeTRDOS = false;

    @Setter
    private DriveStatusListener driveStatusListener;

    public WD1793(MachineSettings settings) {
        this.tStatesPerMs = settings.getMachineType().clockFreq / 1000.0;
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        statusReg = 0;
        commandReg = 0;
        trackReg = 0;
        sectorReg = 1;
        dataReg = 0;

        busy = false;
        drq = false;
        intrq = false;

        motorOn = false;
        isReading = false;
        isWriting = false;

        stateTicks = 0;
        Arrays.fill(lastLedStates, false);
    }

    @Override
    public int inPort(int port) {
        int p = port & 0xFF;

        switch (p) {
            case 0x1F -> {
                intrq = false;

                int s = statusReg;

                // READY: 0 если диск вставлен, мотор НЕ ВАЖЕН
                if (diskInserted[currentDrive]) {
                    s &= ~0x80;
                } else {
                    s |= 0x80;
                }

                if (busy) {
                    s |= 0x01;
                } else {
                    s &= ~0x01;
                }

                if (trackReg == 0) {
                    s |= 0x04;
                } else {
                    s &= ~0x04;
                }

                statusReg = s;
                return s & 0xFF;
            }

            case 0x3F -> {
                return trackReg & 0xFF;
            }

            case 0x5F -> {
                return sectorReg & 0xFF;
            }

            case 0x7F -> {
                return readData() & 0xFF;
            }

            case 0xFF -> {
                int res = 0xFF;

                if (drq) {
                    res &= ~0x80;
                }

                if (intrq) {
                    res &= ~0x40;
                }

                return res;
            }
        }

        return 0xFF;
    }

    @Override
    public boolean isExclusiveValue(int port) {
        return activeTRDOS;
    }

    @Override
    public boolean isIgnoreValue(int port) {
        return !activeTRDOS;
    }

    @Override
    public synchronized void setActive(boolean active) {
        activeTRDOS = active;
    }

    @Override
    public void outPort(int port, int value) {

        int p = port & 0xFF;
        int v = value & 0xFF;

        switch (p) {
            case 0x1F -> executeCommand(v);
            case 0x3F -> trackReg = v;
            case 0x5F -> sectorReg = v;
            case 0x7F -> {
                dataReg = v;
                writeData(v);
            }
            case 0xFF -> handleSystemWrite(v);
            default -> log.warn("Unknown port write: {} = {}", Integer.toHexString(p), Integer.toHexString(v));
        }
    }

    private int readData() {
        drq = false;
        statusReg &= ~0x02;

        if (isReading) {
            int val = sectorBuffer[bufferPtr++] & 0xFF;

            if (bufferPtr >= 256) {
                completeCommand(0);
            } else {
                drq = true;
                statusReg |= 0x02;
            }

            return val;
        }

        return dataReg;
    }

    private void writeData(int value) {
        drq = false;
        statusReg &= ~0x02;

        if (isWriting) {
            sectorBuffer[bufferPtr++] = (byte) value;

            if (bufferPtr >= 256) {
                commitSectorToDisk();
                completeCommand(0);
            } else {
                drq = true;
                statusReg |= 0x02;
            }
        }
    }

    private void executeCommand(int cmd) {
        commandReg = cmd;
        busy = true;
        drq = false;
        intrq = false;

        statusReg |= 0x01;

        if ((cmd & 0xF0) == 0xD0) {
            busy = false;
            isReading = false;
            isWriting = false;
            statusReg &= ~0x01;
            intrq = false;
            return;
        }

        if ((cmd & 0x80) == 0) {
            if ((cmd & 0xF0) == 0x00) {
                trackReg = 0;
            }
            stateTicks = (long) (10 * tStatesPerMs);
        } else {
            if (!diskInserted[currentDrive]) {
                completeCommand(0x80);
                return;
            }

            isReading = (cmd & 0xE0) == 0x80;
            isWriting = (cmd & 0xE0) == 0xA0;
            bufferPtr = 0;
            stateTicks = (long) (1 * tStatesPerMs);
        }

        checkLedEvents();
    }

    @Override
    public void ticks(long tStates, int delta) {
        if (!busy) return;

        if (stateTicks > 0) {
            stateTicks -= delta;
            return;
        }

        if ((commandReg & 0x80) != 0) {
            if (isReading) {
                if (fillBufferFromDisk()) {
                    drq = true;
                    statusReg |= 0x02;
                } else {
                    completeCommand(0x10);
                }
            } else if (isWriting) {
                drq = true;
                statusReg |= 0x02;
            }
        } else {
            completeCommand(0);
        }
    }

    private void completeCommand(int error) {
        busy = false;
        isReading = false;
        isWriting = false;

        statusReg &= 0xEC;
        statusReg |= error;

        drq = false;
        intrq = true;

        checkLedEvents();
    }

    private void handleSystemWrite(int value) {
        systemReg = value;

        currentDrive = value & 0x03;
        currentSide = (value & 0x10) != 0 ? 1 : 0;
        motorOn = (value & 0x08) != 0;

        if ((value & 0x04) != 0) {
            resetChipState();
        }

        checkLedEvents();
    }

    private void resetChipState() {
        busy = false;
        drq = false;
        intrq = false;
        isReading = false;
        isWriting = false;
        stateTicks = 0;
    }

    private boolean fillBufferFromDisk() {
        int offset = calculateOffset();

        if (offset < 0 || offset + 255 >= drives[currentDrive].length) {
            return false;
        }

        System.arraycopy(drives[currentDrive], offset, sectorBuffer, 0, 256);
        return true;
    }

    private void commitSectorToDisk() {
        int offset = calculateOffset();

        if (offset >= 0 && offset + 255 < drives[currentDrive].length) {
            System.arraycopy(sectorBuffer, 0, drives[currentDrive], offset, 256);
        }
    }

    private int calculateOffset() {
        int s = Math.clamp(sectorReg - 1, 0, 15);
        int t = Math.clamp(trackReg, 0, 79);
        return ((t * 2 + currentSide) * 16 + s) * 256;
    }

    @Override
    public void loadDisk(int drive, byte[] data) {
        if (drive < 0 || drive >= 4) return;

        drives[drive] = DiskImageAdapter.convertToTrd(data);
        diskInserted[drive] = true;
    }

    @Override
    public boolean isDriveLedOn(int d) {
        return currentDrive == d && (motorOn || busy);
    }

    @Override
    public void triggerNMI() {
        intrq = true;
        motorOn = true;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    private void checkLedEvents() {
        if (driveStatusListener == null) return;

        for (int i = 0; i < 4; i++) {
            boolean cur = isDriveLedOn(i);
            if (cur != lastLedStates[i]) {
                lastLedStates[i] = cur;
                driveStatusListener.onActiveStateChanged(i, cur);
            }
        }
    }
}
