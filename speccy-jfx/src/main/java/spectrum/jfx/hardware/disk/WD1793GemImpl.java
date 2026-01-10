package spectrum.jfx.hardware.disk;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WD1793GemImpl implements DiskController {

    private static final int PORT_CMD_STATUS = 0x1F;
    private static final int PORT_TRACK = 0x3F;
    private static final int PORT_SECTOR = 0x5F;
    private static final int PORT_DATA = 0x7F;
    private static final int PORT_SYSTEM = 0xFF;

    private static final int S_BUSY = 0x01;
    private static final int S_DRQ = 0x02;
    private static final int S_INDEX = 0x02;
    private static final int S_TRACK0 = 0x04;
    private static final int S_NOT_READY = 0x80;

    @Getter
    private boolean active = false;
    private final VirtualDrive[] drives = new VirtualDrive[4];
    private int selectedDriveIdx = 0;
    private int currentSide = 0;

    private int regTrack;
    private int regSector;
    private int regData;
    private int regStatus;

    private boolean intrq = false;
    private boolean drq = false;
    private long currentTStates;
    private long nextEventTStates = 0;

    private enum State {IDLE, SEARCHING, TRANSFERRING}

    private State currentState = State.IDLE;
    private int dataPos = 0;

    @Setter
    private DriveStatusListener driveStatusListener;

    public WD1793GemImpl() {
        for (int i = 0; i < 4; i++) drives[i] = new VirtualDrive();
        reset();
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isExclusiveValue(int port) {
        return true;
    }

    @Override
    public boolean isIgnoreValue(int port) {
        return false;
    }

    @Override
    public void loadDisk(int drive, byte[] data) {
        if (drive >= 0 && drive < 4) {
            drives[drive].data = data;
            drives[drive].hasDisk = (data != null);
            log.info("BDI: Disk loaded drive {}. Size: {} bytes", (char) ('A' + drive), data != null ? data.length : 0);
        }
    }

    @Override
    public void reset() {
        regTrack = 0;
        regSector = 1;
        regStatus = 0;
        intrq = false;
        drq = false;
        currentState = State.IDLE;
    }

    @Override
    public void ticks(long tStates, int delta) {
        this.currentTStates = tStates;

        if (currentState == State.SEARCHING && tStates >= nextEventTStates) {
            currentState = State.TRANSFERRING;
            drq = true;
        }
    }

    @Override
    public int inPort(int port) {
        int port8 = port & 0xFF;
        switch (port8) {
            case PORT_CMD_STATUS:
                int res = regStatus;
                // Всегда подмешиваем BUSY, если мы не в IDLE
                if (currentState != State.IDLE) res |= S_BUSY;
                if (drq) res |= S_DRQ;

                if (drives[selectedDriveIdx].physicalTrack == 0) res |= S_TRACK0;
                if (!drives[selectedDriveIdx].hasDisk) res |= S_NOT_READY;

                // Имитация индекса (вращения диска) только в простое
                if (currentState == State.IDLE && (currentTStates % 70000 < 4000)) res |= S_INDEX;

                return res;

            case PORT_TRACK:
                return regTrack;
            case PORT_SECTOR:
                return regSector;
            case PORT_DATA:
                return readDataByte();

            case PORT_SYSTEM:
                // ВГ93 System Port: Bit 7 = DRQ, Bit 6 = INTRQ (оба инвертированы для Z80)
                int sys = 0x3F;
                if (!drq) sys |= 0x80;   // Если DRQ=0, на шине 1
                if (!intrq) sys |= 0x40; // Если INTRQ=0, на шине 1
                return sys;
        }
        return 0xFF;
    }

    private int readDataByte() {
        if (currentState == State.TRANSFERRING && drq) {
            VirtualDrive drive = drives[selectedDriveIdx];
            // TRD: Track 0 Side 0, then Track 0 Side 1...
            int offset = ((regTrack * 2 + currentSide) * 16 + (regSector - 1)) * 256;

            int b = 0;
            if (drive.hasDisk && offset >= 0 && (offset + dataPos) < drive.data.length) {
                b = drive.data[offset + dataPos] & 0xFF;
            }

            dataPos++;
            drq = false;

            if (dataPos >= 256) {
                log.info("BDI: Sector Done. T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
                finalizeCommand(0);
            } else {
                // Пауза перед следующим байтом (32 мкс для DD диска ~ 112 тактoв)
                nextEventTStates = currentTStates + 112;
                currentState = State.SEARCHING;
            }
            return b;
        }
        return regData;
    }

    @Override
    public void outPort(int port, int value) {
        int port8 = port & 0xFF;
        switch (port8) {
            case PORT_CMD_STATUS:
                startCommand(value);
                break;
            case PORT_TRACK:
                regTrack = value;
                break;
            case PORT_SECTOR:
                regSector = value;
                break;
            case PORT_DATA:
                regData = value;
                break;
            case PORT_SYSTEM:
                handleSystemPort(value);
                break;
        }
    }

    private void handleSystemPort(int value) {
        selectedDriveIdx = value & 0x03;
        currentSide = (value & 0x10) != 0 ? 0 : 1; // Инверсия для TR-DOS
        if ((value & 0x04) == 0) reset();
        log.debug("BDI: Select Drive:{}, Side:{}", selectedDriveIdx, currentSide);
    }

    private void startCommand(int cmd) {
        intrq = false;
        drq = false;

        // Прерывание текущей команды
        if ((cmd & 0xF0) == 0xD0) {
            currentState = State.IDLE;
            regStatus &= ~S_BUSY;
            return;
        }

        currentState = State.SEARCHING; // Сразу входим в состояние работы

        if ((cmd & 0x80) == 0) { // Type I
            if ((cmd & 0xF0) == 0x00) {
                regTrack = 0;
                drives[selectedDriveIdx].physicalTrack = 0;
            }
            if ((cmd & 0xF0) == 0x10) {
                drives[selectedDriveIdx].physicalTrack = regTrack;
            }
            nextEventTStates = currentTStates + 5000; // Задержка на механику
        } else if ((cmd & 0xE0) == 0x80) { // Read Sector
            dataPos = 0;
            nextEventTStates = currentTStates + 2000; // Поиск заголовка
            log.info("BDI: Read Sector T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
        } else {
            nextEventTStates = currentTStates + 1000;
        }
    }

    private void finalizeCommand(int statusFlags) {
        regStatus = statusFlags;
        intrq = true;
        drq = false;
        currentState = State.IDLE;
    }

    private static class VirtualDrive {
        byte[] data;
        boolean hasDisk;
        int physicalTrack;
    }

    @Override
    public boolean isDriveLedOn(int d) {
        return active && selectedDriveIdx == d;
    }

    @Override
    public void init() {
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void triggerNMI() {
    }
}