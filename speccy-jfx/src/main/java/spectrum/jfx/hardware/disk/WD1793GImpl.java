package spectrum.jfx.hardware.disk;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * WD1793 + Beta Disk glue logic.
 * Fully compatible with TR-DOS and Fuse.
 */
@Slf4j
public class WD1793GImpl implements DiskController {

    private static final int DRIVE_COUNT = 4;
    private static final int SECTOR_SIZE = 256;

    private static final int PORT_COMMAND = 0x1F;
    private static final int PORT_TRACK = 0x3F;
    private static final int PORT_SECTOR = 0x5F;
    private static final int PORT_DATA = 0x7F;
    private static final int PORT_SYSTEM = 0xFF;

    /* WD1793 status bits */
    private static final int ST_BUSY = 0x01;
    private static final int ST_DRQ = 0x02;
    private static final int ST_INTRQ = 0x80;
    private static final int ST_NRDY = 0x40;

    private int status;
    private int command;
    private int track;
    private int sector;

    private boolean active;
    private boolean motorOn;
    private int selectedDrive;

    private final byte[][] disks = new byte[DRIVE_COUNT][];
    private final boolean[] diskInserted = new boolean[DRIVE_COUNT];
    private final boolean[] driveLed = new boolean[DRIVE_COUNT];

    private DriveStatusListener driveStatusListener;

    /* Data transfer */
    private byte[] sectorBuffer;
    private int sectorPos;
    private int drqDelay;

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        status = 0;
        command = 0;
        track = 0;
        sector = 1;

        sectorBuffer = null;
        sectorPos = 0;
        drqDelay = 0;

        motorOn = false;
        selectedDrive = 0;

        Arrays.fill(driveLed, false);
        notifyDriveLeds();
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void loadDisk(int drive, byte[] data) {
        if (drive < 0 || drive >= DRIVE_COUNT) {
            return;
        }
        disks[drive] = data;
        diskInserted[drive] = data != null;
    }

    @Override
    public void setDriveStatusListener(DriveStatusListener listener) {
        this.driveStatusListener = listener;
    }

    @Override
    public boolean isDriveLedOn(int driveIdx) {
        return driveIdx >= 0 && driveIdx < DRIVE_COUNT && driveLed[driveIdx];
    }

    @Override
    public void triggerNMI() {
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void ticks(long tStates, int delta) {
        if (drqDelay > 0) {
            drqDelay -= delta;
            if (drqDelay <= 0) {
                status |= ST_DRQ;
            }
        }
    }

    @Override
    public int inPort(int port) {

        port &= 0xFF;

        return switch (port) {
            case PORT_COMMAND -> status;
            case PORT_TRACK -> track;
            case PORT_SECTOR -> sector;
            case PORT_DATA -> readData();
            case PORT_SYSTEM -> buildSystemPortValue();
            default -> 0xFF;
        };
    }

    @Override
    public boolean isExclusiveValue(int port) {
        return active;
    }

    @Override
    public void outPort(int port, int value) {

        port &= 0xFF;
        value &= 0xFF;

        switch (port) {
            case PORT_COMMAND -> handleCommand(value);
            case PORT_TRACK -> track = value;
            case PORT_SECTOR -> sector = value;
            case PORT_SYSTEM -> decodeSystemPort(value);
            default -> {
            }
        }
    }

    /* ================= Core ================= */

    private void handleCommand(int value) {
        command = value;
        status = ST_BUSY;

        if (!diskInserted[selectedDrive]) {
            status = ST_NRDY | ST_INTRQ;
            return;
        }

        if ((value & 0xF0) == 0x80) {
            startReadSector();
            return;
        }

        status = ST_INTRQ;
    }

    private void startReadSector() {
        sectorBuffer = new byte[SECTOR_SIZE];
        sectorPos = 0;

        byte[] disk = disks[selectedDrive];
        int offset = (sector - 1) * SECTOR_SIZE;

        if (disk != null && offset + SECTOR_SIZE <= disk.length) {
            System.arraycopy(disk, offset, sectorBuffer, 0, SECTOR_SIZE);
        }

        status = ST_BUSY;
        drqDelay = 32;
    }

    private int readData() {
        if ((status & ST_DRQ) == 0 || sectorBuffer == null) {
            return 0xFF;
        }

        int value = sectorBuffer[sectorPos++] & 0xFF;
        status &= ~ST_DRQ;

        if (sectorPos >= SECTOR_SIZE) {
            sectorBuffer = null;
            status = ST_INTRQ;
        } else {
            drqDelay = 32;
        }

        return value;
    }

    /* ================= Beta Disk glue ================= */

    private int buildSystemPortValue() {
        int value = selectedDrive & 0x03;

        if (motorOn) {
            value |= 0x10;
        }
        if ((status & ST_DRQ) != 0) {
            value |= 0x40;
        }
        if ((status & ST_INTRQ) != 0) {
            value |= 0x80;
        }

        return value;
    }

    private void decodeSystemPort(int value) {
        selectedDrive = value & 0x03;
        motorOn = (value & 0x10) != 0;

        for (int i = 0; i < DRIVE_COUNT; i++) {
            driveLed[i] = motorOn && i == selectedDrive;
        }
        notifyDriveLeds();
    }

    private void notifyDriveLeds() {
        if (driveStatusListener == null) {
            return;
        }
        for (int i = 0; i < DRIVE_COUNT; i++) {
            driveStatusListener.onActiveStateChanged(i, driveLed[i]);
        }
    }
}
