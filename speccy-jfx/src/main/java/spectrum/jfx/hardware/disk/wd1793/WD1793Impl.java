package spectrum.jfx.hardware.disk.wd1793;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.disk.DiskController;
import spectrum.jfx.hardware.disk.DiskImageAdapter;
import spectrum.jfx.hardware.disk.DriveStatusListener;
import spectrum.jfx.hardware.disk.trdos.TRDOSController;
import spectrum.jfx.hardware.disk.trdos.TRDOSControllerImpl;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.ula.AddressHookController;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.hardware.ula.Ula;

/**
 * Implementation of the Western Digital WD1793 disk controller. (1818ВГ93 Soviet Union version)</br>
 * <a href="https://hansotten.file-hunter.com/technical-info/wd1793/">Data Sheet</a>
 */
@Slf4j
public class WD1793Impl implements DiskController {

    private static final int PORT_CMD_STATUS = 0x1F;
    private static final int PORT_TRACK = 0x3F;
    private static final int PORT_SECTOR = 0x5F;
    private static final int PORT_DATA = 0x7F;
    private static final int PORT_SYSTEM = 0xFF;

    private static final int S_BUSY = 0x01;
    private static final int S_DRQ = 0x02;
    private static final int S_INDEX = 0x02;
    private static final int S_TRACK0 = 0x04;
    private static final int S_LOST_DATA = 0x04;
    private static final int S_CRC_ERR = 0x08;
    private static final int S_RNF = 0x10;
    private static final int S_HEAD_LOADED = 0x20;
    private static final int S_RECORD_TYPE = 0x20;
    private static final int S_WRITE_PROT = 0x40;
    private static final int S_NOT_READY = 0x80;

    @Getter
    private boolean active = false;
    private final VirtualDriveImpl[] drives = new VirtualDriveImpl[4];
    private final MachineSettings machineSettings;
    private int selectedDriveIdx = 0;
    private int currentSide = 0;

    private int regTrack;
    private int regSector;
    private int regData;
    private int regStatus;
    private int commandReg;
    private int lastSystemValue = 0x3C;
    private boolean motorOn = false;

    private boolean intrq = false;
    private boolean drq = false;
    private long currentTStates;
    private long nextEventTStates = 0;

    private int lastSysAnswer = 0;
    private int sameAnswerCounter = 0;

    private enum State {IDLE, SEARCHING, TRANSFERRING}

    private enum StatusType {TYPE_1, TYPE_2}

    private State currentState = State.IDLE;
    private StatusType currentStatusType = StatusType.TYPE_1;
    private int dataPos = 0;
    private int lastStepDir = -1; // Default direction for Step is Out (towards 0)

    @Setter
    private DriveStatusListener driveStatusListener;
    @Setter
    @Getter
    private TRDOSController trdosController;

    public WD1793Impl(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        for (int i = 0; i < 4; i++) drives[i] = new VirtualDriveImpl();
        reset();
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isExclusiveValue(int port) {
        return active;
    }

    @Override
    public boolean isIgnoreValue(int port) {
        return !active;
    }

    @Override
    public void loadDisk(int drive, byte[] data) {
        if (drive >= 0 && drive < 4) {
            drives[drive].data = DiskImageAdapter.convertToTrd(data);
            drives[drive].hasDisk = (drives[drive].data != null);
            log.trace("BDI: Disk loaded drive {}. Size: {} bytes", (char) ('A' + drive), drives[drive].data != null ? drives[drive].data.length : 0);
        }
    }

    @Override
    public void reset() {
        regTrack = 0;
        regSector = 1;
        regStatus = S_TRACK0; // После сброса обычно голова на 0 треке
        intrq = false;
        drq = false;
        commandReg = 0; // Сбрасываем регистр команды
        currentState = State.IDLE;
    }

    // Timeout для DRQ в тактах (~32 мкс для DD диска = ~112 тактов, но даём больше запаса)
    private static final long DRQ_TIMEOUT_TSTATES = 50000; // ~14ms - достаточно для обработки
    private long drqSetTStates = 0;

    @Override
    public int getDiskCount() {
        return drives.length;
    }

    @Override
    public void ticks(long tStates, int delta) {
        this.currentTStates = tStates;

        if (currentState == State.SEARCHING && tStates >= nextEventTStates) {
            if ((commandReg & 0x80) == 0 && (commandReg & 0xF0) != 0xD0) { // Type I (excluding Force Interrupt)
                finalizeCommand(0);
            } else { // Type II / III
                log.trace("BDI: Start Transfer. Command: {}", Integer.toHexString(commandReg));
                currentState = State.TRANSFERRING;
                drq = true;
                drqSetTStates = tStates;
            }
        }

        // Timeout для DRQ - если данные не читаются/пишутся слишком долго,
        // по спецификации WD1793 устанавливается Lost Data и команда завершается
        if (currentState == State.TRANSFERRING && drq && (tStates - drqSetTStates) > DRQ_TIMEOUT_TSTATES) {
            log.warn("BDI: DRQ timeout - Lost Data. Command: {}, dataPos: {}", Integer.toHexString(commandReg), dataPos);
            finalizeCommand(S_LOST_DATA);
        }
    }

    @Override
    public int inPort(int port) {
        int port8 = port & 0xFF;
        switch (port8) {
            case PORT_CMD_STATUS:
                return commandStatus();
            case PORT_TRACK:
                log.trace("In port track. Track: {}", regTrack);
                return regTrack;
            case PORT_SECTOR:
                log.trace("In port sector. Sector: {}", regTrack);
                return regSector;
            case PORT_DATA:
                int data = readDataByte();
                log.trace("BDI: Data IN: {}", Integer.toHexString(data));
                return data;
            case PORT_SYSTEM:
                // ВГ93 System Port: Bit 7 = INTRQ, Bit 6 = DRQ (согласно beta.c)
                int sys = 0;
                if (intrq) sys |= 0x80;
                if (drq) sys |= 0x40;
                logSysAnswer(sys);
                return sys;
            default:
                log.trace("Unexpected port {}", port8);
        }
        return 0xFF;
    }

    private void logSysAnswer(int sys) {
        if (lastSysAnswer == sys) {
            sameAnswerCounter++;
            if (sameAnswerCounter >= 1000) {
                log.trace("In port system. Answer: {}, Answered: {} times", lastSysAnswer, sameAnswerCounter);
                sameAnswerCounter = 0;
            }
        } else {
            if (sameAnswerCounter > 0) {
                log.trace("In port system. Answer: {}, Answered: {} times", lastSysAnswer, sameAnswerCounter);
            }
            sameAnswerCounter = 0;
            lastSysAnswer = sys;
            log.trace("In port system. Answer: {}", sys);
        }
    }

    private int commandStatus() {
        log.trace("In port command status {}", PORT_CMD_STATUS);
        intrq = false; // Reading status register clears INTRQ
        int res = regStatus;

        // ВГ93: Бит BUSY (0) всегда отражает текущее состояние контроллера
        if (currentState != State.IDLE) {
            res |= S_BUSY;
        } else {
            res &= ~S_BUSY;
        }

        // DRQ всегда отражает текущее состояние передачи
        if (drq) {
            res |= S_DRQ;
        } else {
            res &= ~S_DRQ;
        }

        // Index pulse simulation: в ВГ93 доступен в Idle и при командах Type I
        // В Type II/III на этом месте бит DRQ, который в Idle всегда 0.
        if (currentStatusType == StatusType.TYPE_1 || currentState == State.IDLE) {
            if (currentTStates % 700000 < 14000) { // Impulse ~4ms
                res |= S_INDEX;
            }
        }

        // Специфичные биты для Type I (Track 0, Head Loaded)
        if (currentStatusType == StatusType.TYPE_1) {
            // Track 0
            if (drives[selectedDriveIdx].physicalTrack == 0) {
                res |= S_TRACK0;
            } else {
                res &= ~S_TRACK0;
            }

            // Head Loaded
            if (motorOn) {
                res |= S_HEAD_LOADED;
            } else {
                res &= ~S_HEAD_LOADED;
            }
        }

        // Ready bit (7) - инвертированный READY сигнал с дисковода
        if (!drives[selectedDriveIdx].hasDisk || !motorOn) {
            res |= S_NOT_READY;
        } else {
            res &= ~S_NOT_READY;
        }

        log.trace("BDI: Status IN: {} (State:{}, StatusType:{}, Drive:{}, T:{})",
                Integer.toHexString(res), currentState, currentStatusType, selectedDriveIdx, drives[selectedDriveIdx].physicalTrack);
        return res;
    }

    private int readDataByte() {
        if (currentState == State.TRANSFERRING && drq) {
            if ((commandReg & 0xF0) == 0xC0) { // Read Address
                return readAddressByte();
            }
            if ((commandReg & 0xF0) == 0xE0) { // Read Track
                return readTrackByte();
            }

            VirtualDriveImpl drive = drives[selectedDriveIdx];
            // TRD: Track 0 Side 0, then Track 0 Side 1...
            // TR-DOS использует 16 секторов на трек
            int trackOffset = (regTrack * 2 + currentSide) * 16 * 256;
            int sectorOffset = (regSector - 1) * 256;
            int offset = trackOffset + sectorOffset;

            int b = 0;
            if (drive.hasDisk && offset >= 0 && (offset + dataPos) < drive.data.length) {
                b = drive.data[offset + dataPos] & 0xFF;
                if (dataPos == 0 || dataPos == 255) {
                    log.trace("BDI: Read byte at pos {}: {}", dataPos, Integer.toHexString(b));
                }
                if (regTrack == 0 && regSector == 9 && dataPos == 231) {
                    log.trace("BDI: System sector ID byte (pos 231): {}", Integer.toHexString(b));
                }
            } else if (drive.hasDisk) {
                log.warn("BDI: Read out of bounds! T:{}, S:{}, Side:{}, Offset:{}, Pos:{}", regTrack, regSector, currentSide, offset, dataPos);
            }

            dataPos++;
            drq = false;

            if (dataPos >= 256) {
                log.trace("BDI: Sector Done. T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);

                // Multi-sector read support (bit 4 'm' is set)
                if ((commandReg & 0x10) != 0) {
                    regSector++;
                    if (regSector <= 16) {
                        dataPos = 0;
                        nextEventTStates = currentTStates + 1000; // Delay before next sector
                        currentState = State.SEARCHING;
                        return b;
                    }
                }
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

    private void writeDataByte(int b) {
        if (currentState == State.TRANSFERRING && drq) {
            drq = false;

            if ((commandReg & 0xE0) == 0xA0) { // Write Sector
                VirtualDriveImpl drive = drives[selectedDriveIdx];
                int trackOffset = (regTrack * 2 + currentSide) * 16 * 256;
                int sectorOffset = (regSector - 1) * 256;
                int offset = trackOffset + sectorOffset;

                if (drive.hasDisk && drive.data != null && offset >= 0 && (offset + dataPos) < drive.data.length) {
                    // Write byte to disk
                    drive.data[offset + dataPos] = (byte) b;
                    drive.setDirty(true);
                }

                dataPos++;
                if (dataPos >= 256) {
                    log.trace("BDI: Write Sector Done. T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);

                    // Multi-sector write support (bit 4 'm' is set)
                    if ((commandReg & 0x10) != 0) {
                        regSector++;
                        if (regSector <= 16) {
                            dataPos = 0;
                            nextEventTStates = currentTStates + 1000;
                            currentState = State.SEARCHING;
                            return;
                        }
                    }
                    finalizeCommand(0);
                } else {
                    nextEventTStates = currentTStates + 112;
                    currentState = State.SEARCHING;
                }
            } else if ((commandReg & 0xF0) == 0xF0) { // Write Track (Format)
                // Write Track: данные просто принимаются и игнорируются (форматирование TRD не требуется)
                // DRQ устанавливается сразу после каждого байта
                dataPos++;
                if (dataPos >= 6250) { // Approximate track size
                    log.trace("BDI: Write Track Done. T:{}, Side:{}", regTrack, currentSide);
                    finalizeCommand(0);
                } else {
                    // DRQ сразу готов для следующего байта (без задержки)
                    drq = true;
                }
            }
        }
    }

    private int readTrackByte() {
        VirtualDriveImpl drive = drives[selectedDriveIdx];
        int trackOffset = (regTrack * 2 + currentSide) * 16 * 256;
        int b = 0;
        if (drive.hasDisk && (trackOffset + dataPos) < drive.data.length) {
            b = drive.data[trackOffset + dataPos] & 0xFF;
        }
        dataPos++;
        drq = false;
        // 16 sectors * 256 bytes = 4096 bytes per track per side in TRD
        if (dataPos >= 4096) {
            finalizeCommand(0);
        } else {
            nextEventTStates = currentTStates + 112;
            currentState = State.SEARCHING;
        }
        return b;
    }

    private int readAddressByte() {
        int val;
        switch (dataPos) {
            case 0 -> val = regTrack;
            case 1 -> val = currentSide;
            case 2 -> val = regSector;
            case 3 -> val = 1; // Sector length code (256 bytes)
            case 4, 5 -> val = 0; // CRC
            default -> val = 0;
        }
        log.trace("BDI: Read Address byte {}: {}", dataPos, Integer.toHexString(val & 0xFF));
        dataPos++;
        drq = false;
        if (dataPos >= 6) {
            // WD1793: At the completion of the Read Address command,
            // the sector register contains the track number read from the ID field.
            regSector = regTrack;
            finalizeCommand(0);
        } else {
            nextEventTStates = currentTStates + 112;
            currentState = State.SEARCHING;
        }
        return val & 0xFF;
    }

    @Override
    public void outPort(int port, int value) {
        int port8 = port & 0xFF;
        log.trace("BDI: Port OUT: {} = {}", Integer.toHexString(port8), Integer.toHexString(value));
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
                writeDataByte(value);
                break;
            case PORT_SYSTEM:
                handleSystemPort(value);
                break;
            default:
                log.warn("BDI: Unknown port OUT: {}", Integer.toHexString(port8));
        }
    }

    private void handleSystemPort(int value) {
        selectedDriveIdx = value & 0x03;
        // В TR-DOS бит 4 системного порта выбирает сторону.
        currentSide = (value & 0x10) != 0 ? 0 : 1;

        // Бит 3 (0x08) - управление мотором
        motorOn = (value & 0x08) != 0;

        // Бит 2 (0x04) - сброс ВГ93 (активен низким уровнем)
        if ((value & 0x04) == 0) {
            reset();
        } else if ((lastSystemValue & 0x04) == 0) {
            // Выход из сброса (переход 0 -> 1)
            // По спецификации ВГ93, при выходе из сброса выполняется команда RESTORE (0x03)
            startCommand(0x03);
        }

        lastSystemValue = value;
        log.trace("BDI: Select Drive:{}, Side:{}, Motor:{}, raw:{}", selectedDriveIdx, currentSide, motorOn, Integer.toHexString(value));
    }

    private void startCommand(int cmd) {
        // Прерывание текущей команды (Type IV) - всегда обрабатывается
        if ((cmd & 0xF0) == 0xD0) {
            currentState = State.IDLE;
            currentStatusType = StatusType.TYPE_1;
            intrq = false;
            drq = false;
            // Immediate interrupt requested?
            if ((cmd & 0x08) != 0) {
                intrq = true;
            }
            log.trace("BDI: Command FORCE INTERRUPT. IRQ bit: {}", (cmd & 0x08) != 0);
            return;
        }

        // По спецификации WD1793: команды игнорируются когда контроллер занят (кроме Force Interrupt)
        if (currentState != State.IDLE) {
            log.trace("BDI: Command {} ignored - controller busy", Integer.toHexString(cmd));
            return;
        }

        commandReg = cmd;
        intrq = false;
        drq = false;
        dataPos = 0;
        regStatus = 0; // Clear status at command start (except bits calculated in inPort)

        currentState = State.SEARCHING; // Сразу входим в состояние работы

        if ((cmd & 0x80) == 0) { // Type I
            currentStatusType = StatusType.TYPE_1;
            int type = cmd & 0xF0;
            switch (type) {
                case 0x00 -> { // Restore
                    log.trace("BDI: Command RESTORE");
                    regTrack = 0;
                    drives[selectedDriveIdx].physicalTrack = 0;
                    lastStepDir = -1;
                }
                case 0x10 -> { // Seek
                    log.trace("BDI: Command SEEK to T:{}", regData);
                    // Seek always updates regTrack and moves physical head
                    regTrack = regData;
                    drives[selectedDriveIdx].physicalTrack = regTrack;
                }
                case 0x20, 0x30 -> { // Step
                    log.trace("BDI: Command STEP");
                    if ((cmd & 0x10) != 0) regTrack += lastStepDir;
                    drives[selectedDriveIdx].physicalTrack += lastStepDir;
                }
                case 0x40, 0x50 -> { // Step In
                    log.trace("BDI: Command STEP IN");
                    lastStepDir = 1;
                    if ((cmd & 0x10) != 0) regTrack += lastStepDir;
                    drives[selectedDriveIdx].physicalTrack += lastStepDir;
                }
                case 0x60, 0x70 -> { // Step Out
                    log.trace("BDI: Command STEP OUT");
                    lastStepDir = -1;
                    if ((cmd & 0x10) != 0) regTrack += lastStepDir;
                    drives[selectedDriveIdx].physicalTrack += lastStepDir;
                }
            }
            // Constraints
            if (regTrack < 0) regTrack = 0;
            if (regTrack > 160) regTrack = 160;
            if (drives[selectedDriveIdx].physicalTrack < 0) drives[selectedDriveIdx].physicalTrack = 0;
            if (drives[selectedDriveIdx].physicalTrack > 160) drives[selectedDriveIdx].physicalTrack = 160;

            nextEventTStates = currentTStates + 5000; // Задержка на механику
        } else { // Type II / III
            currentStatusType = StatusType.TYPE_2;
            if (!drives[selectedDriveIdx].hasDisk || !motorOn) {
                log.warn("BDI: Command {} failed: Not Ready (Motor:{}, Disk:{})", Integer.toHexString(cmd), motorOn, drives[selectedDriveIdx].hasDisk);
                finalizeCommand(S_NOT_READY);
                return;
            }

            if ((cmd & 0xE0) == 0x80) { // Read Sector
                dataPos = 0;
                nextEventTStates = currentTStates + 30000; // Имитация поиска сектора (~8.5мс)
                log.trace("BDI: Command READ SECTOR T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
            } else if ((cmd & 0xE0) == 0xA0) { // Write Sector
                dataPos = 0;
                nextEventTStates = currentTStates + 30000;
                log.trace("BDI: Command WRITE SECTOR T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
            } else if ((cmd & 0xF0) == 0xC0) { // Read Address
                dataPos = 0;
                nextEventTStates = currentTStates + 15000;
                log.trace("BDI: Command READ ADDRESS");
            } else if ((cmd & 0xF0) == 0xE0) { // Read Track
                dataPos = 0;
                nextEventTStates = currentTStates + 50000;
                log.trace("BDI: Command READ TRACK");
            } else if ((cmd & 0xF0) == 0xF0) { // Write Track (Format)
                // Для TRD образов форматирование не требуется - они уже предформатированы.
                // Завершаем команду сразу с успехом, как делают многие эмуляторы.
                log.trace("BDI: Command WRITE TRACK (Format) - completing immediately (TRD pre-formatted)");
                finalizeCommand(0);
            } else {
                nextEventTStates = currentTStates + 5000;
                log.trace("BDI: Command UNKNOWN: {}", Integer.toHexString(cmd));
            }
        }
    }

    private void finalizeCommand(int statusFlags) {
        log.trace("BDI: Finalize Command: {}, Status: {}", Integer.toHexString(commandReg), Integer.toHexString(statusFlags));
        regStatus = statusFlags;
        intrq = true;
        drq = false;
        currentState = State.IDLE;
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

    @Override
    public boolean initWithULA(Ula ula) {
        initTRDOS(ula);
        assignClock(ula);
        return true;
    }

    private void initTRDOS(Ula ula) {
        trdosController = new TRDOSControllerImpl(machineSettings, this, ula.getMemory());
        ula.addAddressHookController((AddressHookController) trdosController);
        assignClock(ula);
        assignPorts(ula);
    }

    private void assignClock(Ula ula) {
        ula.addClockListener(this);
    }

    private void assignPorts(Ula ula) {
        /*
         * InPorts 0x1F, 0x3F,0x5F,0x7F,0xFF
         */
        ula.addPortListener(PORT_CMD_STATUS, (InPortListener) this);
        ula.addPortListener(PORT_TRACK, (InPortListener) this);
        ula.addPortListener(PORT_SECTOR, (InPortListener) this);
        ula.addPortListener(PORT_DATA, (InPortListener) this);
        ula.addPortListener(PORT_SYSTEM, (InPortListener) this);

        /*
         * OutPorts 0x1F,0x3F,0x5F,0x7F,0xFF
         */
        ula.addPortListener(PORT_CMD_STATUS, (OutPortListener) this);
        ula.addPortListener(PORT_TRACK, (OutPortListener) this);
        ula.addPortListener(PORT_SECTOR, (OutPortListener) this);
        ula.addPortListener(PORT_DATA, (OutPortListener) this);
        ula.addPortListener(PORT_SYSTEM, (OutPortListener) this);
    }

}