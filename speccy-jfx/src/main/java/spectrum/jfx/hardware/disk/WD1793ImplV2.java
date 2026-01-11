package spectrum.jfx.hardware.disk;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WD1793ImplV2 implements DiskController {

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
    private int commandReg;
    private boolean motorOn = false;

    private boolean intrq = false;
    private boolean drq = false;
    private long currentTStates;
    private long nextEventTStates = 0;

    private enum State {IDLE, SEARCHING, TRANSFERRING}

    private State currentState = State.IDLE;
    private int dataPos = 0;

    @Setter
    @Getter
    private TRDOSController trdosController;

    @Setter
    private DriveStatusListener driveStatusListener;

    public WD1793ImplV2() {
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
            drives[drive].data = DiskImageAdapter.convertToTrd(data);
            drives[drive].hasDisk = (drives[drive].data != null);
            log.info("BDI: Disk loaded drive {}. Size: {} bytes", (char) ('A' + drive), drives[drive].data != null ? drives[drive].data.length : 0);
        }
    }

    @Override
    public void reset() {
        regTrack = 0;
        regSector = 1;
        regStatus = S_TRACK0;
        intrq = false;
        drq = false;
        currentState = State.IDLE;
        log.info("BDI: FULL RESET - Diagnostic logs active.");
    }

    @Override
    public void ticks(long tStates, int delta) {
        this.currentTStates = tStates;

        if (currentState != State.IDLE && tStates >= nextEventTStates) {
            if (currentState == State.SEARCHING) {
                if ((commandReg & 0x80) == 0) { // Type I commands (Restore, Seek, Step)
                    finalizeCommand(0);
                } else { // Type II (Read/Write Sector)
                    currentState = State.TRANSFERRING;
                    dataPos = 0;
                    drq = true; // Выставляем первый байт
                    nextEventTStates = tStates + 128; // Задержка между байтами
                    log.debug("BDI: Starting transfer for cmd {}", Integer.toHexString(commandReg));
                }
            } else if (currentState == State.TRANSFERRING) {
                if (!drq) { // Если предыдущий байт был прочитан, готовим следующий
                    drq = true;
                    nextEventTStates = tStates + 128;
                } else {
                    // Если Z80 не успел прочитать байт, ждем еще немного (Data Lost имитация)
                    nextEventTStates = tStates + 50;
                }
            }
        }
    }

    @Override
    public int inPort(int port) {
        int port8 = port & 0xFF;
        switch (port8) {
            case PORT_CMD_STATUS:
                intrq = false;
                int res = regStatus;
                if (currentState != State.IDLE) res |= S_BUSY;
                if (drq) res |= S_DRQ;
                if (currentState == State.IDLE) {
                    if (drives[selectedDriveIdx].physicalTrack == 0) res |= S_TRACK0;
                    if (currentTStates % 700000 < 14000) res |= S_INDEX;
                }
                if (!motorOn || !drives[selectedDriveIdx].hasDisk) res |= S_NOT_READY;
                return res;
            case PORT_TRACK:
                return regTrack;
            case PORT_SECTOR:
                return regSector;
            case PORT_DATA:
                return readDataByte();
            case PORT_SYSTEM:
                int sys = 0x3F;
                if (intrq) sys |= 0x80;
                if (drq) sys |= 0x40;
                return sys;
        }
        return 0xFF;
    }

    private int readDataByte() {
        if (currentState != State.TRANSFERRING || !drq) {
            return regData;
        }

        int b = 0;
        VirtualDrive drive = drives[selectedDriveIdx];

        if ((commandReg & 0xF0) == 0xC0) { // Read Address
            b = getAddressByte();
        } else { // Read Sector
            int offset = (regTrack * 2 + currentSide) * 16 * 256 + (regSector - 1) * 256 + dataPos;
            if (drive.hasDisk && offset < drive.data.length) {
                b = drive.data[offset] & 0xFF;
            }
        }

        regData = b;
        drq = false; // Байт забран процессором
        dataPos++;

        int targetLen = ((commandReg & 0xF0) == 0xC0) ? 6 : 256;
        if (dataPos >= targetLen) {
            if ((commandReg & 0x10) != 0 && (commandReg & 0xE0) == 0x80 && regSector < 16) {
                regSector++;
                dataPos = 0;
                currentState = State.SEARCHING;
                nextEventTStates = currentTStates + 2000;
                log.debug("BDI: Multi-sector read. Moving to sector {}", regSector);
            } else {
                finalizeCommand(0);
            }
        }
        return b;
    }

    private int getAddressByte() {
        return switch (dataPos) {
            case 0 -> regTrack;
            case 1 -> currentSide;
            case 2 -> regSector;
            case 3 -> 1; // 256 bytes sector size code
            default -> 0;
        };
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
        currentSide = (value & 0x10) != 0 ? 0 : 1;
        motorOn = (value & 0x08) != 0;
        if ((value & 0x04) == 0) reset();
    }

    private void startCommand(int cmd) {
        commandReg = cmd;
        intrq = false;
        drq = false;
        dataPos = 0;
        regStatus = 0;

        if ((cmd & 0xF0) == 0xD0) { // Force Interrupt
            currentState = State.IDLE;
            if ((cmd & 0x0F) != 0) intrq = true;
            return;
        }

        currentState = State.SEARCHING;
        // Даем время TR-DOS увидеть BUSY перед началом передачи
        nextEventTStates = currentTStates + 15000;

        if ((cmd & 0x80) == 0) { // Type I
            if ((cmd & 0xF0) == 0x00) {
                regTrack = 0;
                drives[selectedDriveIdx].physicalTrack = 0;
            }
            if ((cmd & 0xF0) == 0x10) {
                regTrack = regData;
                drives[selectedDriveIdx].physicalTrack = regData;
            }
        }
        log.info("BDI: CMD {} started. T:{}, S:{}, Side:{}", Integer.toHexString(cmd), regTrack, regSector, currentSide);
    }

    private void finalizeCommand(int status) {
        regStatus = status;
        currentState = State.IDLE;
        drq = false;
        intrq = true;
        log.info("BDI: CMD finished. Status: {}", status);
    }

    private static class VirtualDrive {
        byte[] data;
        boolean hasDisk;
        int physicalTrack;
    }

    @Override
    public boolean isDriveLedOn(int d) {
        return active && selectedDriveIdx == d && motorOn;
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