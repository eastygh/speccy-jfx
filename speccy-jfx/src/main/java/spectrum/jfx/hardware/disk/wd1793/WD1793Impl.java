package spectrum.jfx.hardware.disk.wd1793;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.disk.DiskController;
import spectrum.jfx.hardware.disk.DriveStatusListener;
import spectrum.jfx.hardware.disk.VirtualDrive;
import spectrum.jfx.hardware.disk.trdos.TRDOSController;
import spectrum.jfx.hardware.disk.trdos.TRDOSControllerImpl;
import spectrum.jfx.hardware.machine.MachineSettings;
import spectrum.jfx.hardware.ula.AddressHookController;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.hardware.ula.Ula;

import static spectrum.jfx.hardware.disk.wd1793.WD1793Constants.*;

/**
 * Implementation of the Western Digital WD1793 Floppy Disk Controller.
 * <p>
 * This is the Soviet 1818ВГ93 variant used in Beta Disk Interface for ZX Spectrum.
 *
 * @see <a href="https://hansotten.file-hunter.com/technical-info/wd1793/">WD1793 Data Sheet</a>
 */
@Slf4j
public class WD1793Impl implements DiskController {

    private static final int DRIVE_COUNT = 4;

    // Hardware components
    private final VirtualDrive[] drives = new VirtualDriveImpl[DRIVE_COUNT];
    private final MachineSettings machineSettings;

    // Drive selection and configuration
    @Getter
    private boolean active = false;
    private int selectedDriveIdx = 0;
    private int currentSide = 0;
    private boolean motorOn = false;
    private int lastSystemValue = 0x3C;

    // WD1793 registers
    private int regTrack;
    private int regSector;
    private int regData;
    private int regStatus;
    private int commandReg;

    // Interrupt and data request signals
    private boolean intrq = false;
    private boolean drq = false;

    // Timing state
    private long currentTStates;
    private long nextEventTStates = 0;
    private long drqSetTStates = 0;

    // Controller state
    private ControllerState currentState = ControllerState.IDLE;
    private StatusType currentStatusType = StatusType.TYPE_1;
    private int dataPos = 0;
    private int lastStepDir = STEP_OUT;

    // Debug logging state
    private int lastSysAnswer = 0;
    private int sameAnswerCounter = 0;

    // External components
    @Setter
    private DriveStatusListener driveStatusListener;
    @Setter
    @Getter
    private TRDOSController trdosController;

    public WD1793Impl(MachineSettings machineSettings) {
        this.machineSettings = machineSettings;
        initializeDrives();
        reset();
    }

    private void initializeDrives() {
        for (int i = 0; i < DRIVE_COUNT; i++) {
            drives[i] = new VirtualDriveImpl();
        }
    }

    // ========== DiskController Interface ==========

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
    public void reset() {
        regTrack = 0;
        regSector = DEFAULT_SECTOR;
        regStatus = STATUS_TRACK0;
        intrq = false;
        drq = false;
        commandReg = 0;
        currentState = ControllerState.IDLE;
    }

    @Override
    public int getDiskCount() {
        return drives.length;
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
    public VirtualDrive getDrive(int driveIdx) {
        return drives[driveIdx];
    }

    // ========== Clock Listener ==========

    @Override
    public void ticks(long tStates, int delta) {
        this.currentTStates = tStates;
        processSearchingState(tStates);
        checkDrqTimeout(tStates);
    }

    private void processSearchingState(long tStates) {
        if (currentState != ControllerState.SEARCHING || tStates < nextEventTStates) {
            return;
        }

        CommandType cmdType = CommandType.fromCommand(commandReg);
        if (cmdType.isTypeI()) {
            finalizeCommand(0);
        } else {
            startDataTransfer(tStates);
        }
    }

    private void startDataTransfer(long tStates) {
        log.trace("BDI: Start Transfer. Command: {}", Integer.toHexString(commandReg));
        currentState = ControllerState.TRANSFERRING;
        drq = true;
        drqSetTStates = tStates;
    }

    private void checkDrqTimeout(long tStates) {
        if (currentState != ControllerState.TRANSFERRING || !drq) {
            return;
        }

        if ((tStates - drqSetTStates) > TIMING_DRQ_TIMEOUT) {
            log.warn("BDI: DRQ timeout - Lost Data. Command: {}, dataPos: {}",
                    Integer.toHexString(commandReg), dataPos);
            finalizeCommand(STATUS_LOST_DATA);
        }
    }

    // ========== Port I/O ==========

    @Override
    public int inPort(int port) {
        int port8 = port & 0xFF;
        return switch (port8) {
            case PORT_CMD_STATUS -> readStatusRegister();
            case PORT_TRACK -> readTrackRegister();
            case PORT_SECTOR -> readSectorRegister();
            case PORT_DATA -> readDataRegister();
            case PORT_SYSTEM -> readSystemPort();
            default -> handleUnknownPortRead(port8);
        };
    }

    @Override
    public void outPort(int port, int value) {
        int port8 = port & 0xFF;
        log.trace("BDI: Port OUT: {} = {}", Integer.toHexString(port8), Integer.toHexString(value));

        switch (port8) {
            case PORT_CMD_STATUS -> startCommand(value);
            case PORT_TRACK -> regTrack = value;
            case PORT_SECTOR -> regSector = value;
            case PORT_DATA -> writeDataRegister(value);
            case PORT_SYSTEM -> handleSystemPort(value);
            default -> log.warn("BDI: Unknown port OUT: {}", Integer.toHexString(port8));
        }
    }

    // ========== Register Read Operations ==========

    private int readStatusRegister() {
        log.trace("In port command status {}", PORT_CMD_STATUS);
        intrq = false; // Reading status register clears INTRQ
        return calculateStatus();
    }

    private int readTrackRegister() {
        log.trace("In port track. Track: {}", regTrack);
        return regTrack;
    }

    private int readSectorRegister() {
        log.trace("In port sector. Sector: {}", regSector);
        return regSector;
    }

    private int readDataRegister() {
        int data = readDataByte();
        log.trace("BDI: Data IN: {}", Integer.toHexString(data));
        return data;
    }

    private int readSystemPort() {
        int sys = buildSystemPortValue();
        logSystemPortAnswer(sys);
        return sys;
    }

    private int buildSystemPortValue() {
        int sys = 0;
        if (intrq) sys |= SYS_INTRQ;
        if (drq) sys |= SYS_DRQ;
        return sys;
    }

    private int handleUnknownPortRead(int port8) {
        log.warn("Unexpected port {}", port8);
        return 0xFF;
    }

    // ========== Status Register Calculation ==========

    private int calculateStatus() {
        int status = regStatus;
        status = applyBusyFlag(status);
        status = applyDrqFlag(status);
        status = applyIndexPulse(status);
        status = applyType1StatusBits(status);
        status = applyReadyFlag(status);

        logStatus(status);
        return status;
    }

    private int applyBusyFlag(int status) {
        if (currentState != ControllerState.IDLE) {
            return status | STATUS_BUSY;
        }
        return status & ~STATUS_BUSY;
    }

    private int applyDrqFlag(int status) {
        if (drq) {
            return status | STATUS_DRQ;
        }
        return status & ~STATUS_DRQ;
    }

    private int applyIndexPulse(int status) {
        // Index pulse available in IDLE state and for Type I commands
        if (currentStatusType == StatusType.TYPE_1 || currentState == ControllerState.IDLE) {
            if (isIndexPulseActive()) {
                return status | STATUS_INDEX;
            }
        }
        return status;
    }

    private boolean isIndexPulseActive() {
        return currentTStates % TIMING_INDEX_PERIOD < TIMING_INDEX_PULSE;
    }

    private int applyType1StatusBits(int status) {
        if (currentStatusType != StatusType.TYPE_1) {
            return status;
        }

        // Track 0 indicator
        if (getSelectedDrive().getPhysicalTrack() == 0) {
            status |= STATUS_TRACK0;
        } else {
            status &= ~STATUS_TRACK0;
        }

        // Head loaded indicator
        if (motorOn) {
            status |= STATUS_HEAD_LOADED;
        } else {
            status &= ~STATUS_HEAD_LOADED;
        }

        return status;
    }

    private int applyReadyFlag(int status) {
        // Ready bit (7) - inverted READY signal from drive
        if (!getSelectedDrive().isHasDisk() || !motorOn) {
            return status | STATUS_NOT_READY;
        }
        return status & ~STATUS_NOT_READY;
    }

    private void logStatus(int status) {
        log.trace("BDI: Status IN: {} (State:{}, StatusType:{}, Drive:{}, T:{})",
                Integer.toHexString(status), currentState, currentStatusType,
                selectedDriveIdx, getSelectedDrive().getPhysicalTrack());
    }

    // ========== Data Read Operations ==========

    private int readDataByte() {
        if (currentState != ControllerState.TRANSFERRING || !drq) {
            return regData;
        }

        CommandType cmdType = CommandType.fromCommand(commandReg);
        return switch (cmdType) {
            case READ_ADDRESS -> readAddressByte();
            case READ_TRACK -> readTrackDataByte();
            default -> readSectorDataByte();
        };
    }

    private int readSectorDataByte() {
        VirtualDrive drive = getSelectedDrive();
        int offset = TrdDiskGeometry.calculateOffset(regTrack, currentSide, regSector);

        int byteValue = 0;
        if (drive.isHasDisk() && isValidOffset(offset + dataPos, drive)) {
            byteValue = drive.readByte(offset + dataPos) & 0xFF;
            logSectorRead(byteValue);
        } else if (drive.isHasDisk()) {
            logOutOfBoundsRead(offset);
        }

        advanceDataPosition();
        return checkSectorComplete(byteValue);
    }

    private void logSectorRead(int byteValue) {
        if (dataPos == 0 || dataPos == TrdDiskGeometry.BYTES_PER_SECTOR - 1) {
            log.trace("BDI: Read byte at pos {}: {}", dataPos, Integer.toHexString(byteValue));
        }
        // System sector identification check
        if (regTrack == 0 && regSector == 9 && dataPos == 231) {
            log.trace("BDI: System sector ID byte (pos 231): {}", Integer.toHexString(byteValue));
        }
    }

    private void logOutOfBoundsRead(int offset) {
        log.warn("BDI: Read out of bounds! T:{}, S:{}, Side:{}, Offset:{}, Pos:{}",
                regTrack, regSector, currentSide, offset, dataPos);
    }

    private void advanceDataPosition() {
        dataPos++;
        drq = false;
    }

    private int checkSectorComplete(int byteValue) {
        if (dataPos >= TrdDiskGeometry.BYTES_PER_SECTOR) {
            log.trace("BDI: Sector Done. T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
            return handleSectorComplete(byteValue);
        }

        scheduleNextByte();
        return byteValue;
    }

    private int handleSectorComplete(int byteValue) {
        // Multi-sector read support (bit 4 'm' is set)
        if (isMultiSectorCommand()) {
            regSector++;
            if (regSector <= TrdDiskGeometry.MAX_SECTOR) {
                dataPos = 0;
                scheduleNextSector();
                return byteValue;
            }
        }
        finalizeCommand(0);
        return byteValue;
    }

    private boolean isMultiSectorCommand() {
        return (commandReg & CMD_MULTI_SECTOR) != 0;
    }

    private void scheduleNextByte() {
        nextEventTStates = currentTStates + TIMING_BYTE_INTERVAL;
        currentState = ControllerState.SEARCHING;
    }

    private void scheduleNextSector() {
        nextEventTStates = currentTStates + TIMING_INTER_SECTOR;
        currentState = ControllerState.SEARCHING;
    }

    private int readTrackDataByte() {
        VirtualDrive drive = getSelectedDrive();
        int trackOffset = TrdDiskGeometry.calculateTrackOffset(regTrack, currentSide);

        int byteValue = 0;
        if (drive.isHasDisk() && isValidOffset(trackOffset + dataPos, drive)) {
            byteValue = drive.readByte(trackOffset + dataPos) & 0xFF;
        }

        advanceDataPosition();

        if (dataPos >= TrdDiskGeometry.BYTES_PER_TRACK) {
            finalizeCommand(0);
        } else {
            scheduleNextByte();
        }
        return byteValue;
    }

    private int readAddressByte() {
        int value = switch (dataPos) {
            case 0 -> regTrack;
            case 1 -> currentSide;
            case 2 -> regSector;
            case 3 -> TrdDiskGeometry.SECTOR_SIZE_CODE;
            case 4, 5 -> 0; // CRC bytes
            default -> 0;
        };

        log.trace("BDI: Read Address byte {}: {}", dataPos, Integer.toHexString(value & 0xFF));
        advanceDataPosition();

        if (dataPos >= TrdDiskGeometry.ADDRESS_FIELD_SIZE) {
            // Per WD1793 spec: sector register receives track number from ID field
            regSector = regTrack;
            finalizeCommand(0);
        } else {
            scheduleNextByte();
        }
        return value & 0xFF;
    }

    // ========== Data Write Operations ==========

    private void writeDataRegister(int value) {
        regData = value;
        writeDataByte(value);
    }

    private void writeDataByte(int byteValue) {
        if (currentState != ControllerState.TRANSFERRING || !drq) {
            return;
        }

        drq = false;
        CommandType cmdType = CommandType.fromCommand(commandReg);

        switch (cmdType) {
            case WRITE_SECTOR -> writeSectorDataByte(byteValue);
            case WRITE_TRACK -> writeTrackDataByte();
            default -> {
            }
        }
    }

    private void writeSectorDataByte(int byteValue) {
        VirtualDrive drive = getSelectedDrive();
        int offset = TrdDiskGeometry.calculateOffset(regTrack, currentSide, regSector);

        if (drive.isHasDisk() && isValidOffset(offset + dataPos, drive)) {
            if (!drive.writeByte(offset + dataPos, (byte) byteValue)) {
                log.warn("BDI: Write failed! T:{}, S:{}, Side:{}, Offset:{}, Pos:{}",
                        regTrack, regSector, currentSide, offset, dataPos);
            }
        }

        dataPos++;
        if (dataPos >= TrdDiskGeometry.BYTES_PER_SECTOR) {
            log.trace("BDI: Write Sector Done. T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
            handleWriteSectorComplete();
        } else {
            scheduleNextByte();
        }
    }

    private void handleWriteSectorComplete() {
        if (isMultiSectorCommand()) {
            regSector++;
            if (regSector <= TrdDiskGeometry.MAX_SECTOR) {
                dataPos = 0;
                scheduleNextSector();
                return;
            }
        }
        finalizeCommand(0);
    }

    private void writeTrackDataByte() {
        // Write Track (Format): data is accepted but ignored for TRD images
        // TRD images are pre-formatted, actual formatting is not needed
        dataPos++;
        if (dataPos >= TrdDiskGeometry.RAW_TRACK_SIZE) {
            log.trace("BDI: Write Track Done. T:{}, Side:{}", regTrack, currentSide);
            finalizeCommand(0);
        } else {
            // DRQ immediately ready for next byte (no delay)
            drq = true;
        }
    }

    // ========== System Port Handling ==========

    private void handleSystemPort(int value) {
        selectedDriveIdx = value & SYS_DRIVE_MASK;

        // Bit 4 selects side (active low = side 1)
        currentSide = (value & SYS_SIDE) != 0 ? 0 : 1;

        // Bit 3 controls motor
        motorOn = (value & SYS_MOTOR) != 0;

        handleResetLogic(value);

        lastSystemValue = value;
        log.trace("BDI: Select Drive:{}, Side:{}, Motor:{}, raw:{}",
                selectedDriveIdx, currentSide, motorOn, Integer.toHexString(value));
    }

    private void handleResetLogic(int value) {
        // Bit 2 (0x04) - controller reset (active low)
        if ((value & SYS_RESET) == 0) {
            reset();
        } else if ((lastSystemValue & SYS_RESET) == 0) {
            // Exiting reset (0 -> 1 transition)
            // Per WD1793 spec: RESTORE command (0x03) is executed on reset exit
            startCommand(0x03);
        }
    }

    // ========== Command Execution ==========

    private void startCommand(int cmd) {
        CommandType cmdType = CommandType.fromCommand(cmd);

        // Force Interrupt is always processed, even when busy
        if (cmdType == CommandType.FORCE_INTERRUPT) {
            executeForceInterrupt(cmd);
            return;
        }

        // Per WD1793 spec: commands are ignored when controller is busy
        if (currentState != ControllerState.IDLE) {
            log.trace("BDI: Command {} ignored - controller busy", Integer.toHexString(cmd));
            return;
        }

        initializeCommand(cmd, cmdType);

        if (cmdType.isTypeI()) {
            executeTypeICommand(cmd, cmdType);
        } else {
            executeTypeIIOrIIICommand(cmd, cmdType);
        }
    }

    private void initializeCommand(int cmd, CommandType cmdType) {
        commandReg = cmd;
        intrq = false;
        drq = false;
        dataPos = 0;
        regStatus = 0;
        currentState = ControllerState.SEARCHING;
        currentStatusType = cmdType.getStatusType();
    }

    private void executeForceInterrupt(int cmd) {
        currentState = ControllerState.IDLE;
        currentStatusType = StatusType.TYPE_1;
        intrq = false;
        drq = false;

        // Check if immediate interrupt is requested
        if ((cmd & CMD_IMMEDIATE_IRQ) != 0) {
            intrq = true;
        }
        log.trace("BDI: Command FORCE INTERRUPT. IRQ bit: {}", (cmd & CMD_IMMEDIATE_IRQ) != 0);
    }

    private void executeTypeICommand(int cmd, CommandType cmdType) {
        switch (cmdType) {
            case RESTORE -> executeRestore();
            case SEEK -> executeSeek();
            case STEP -> executeStep(cmd);
            case STEP_IN -> executeStepIn(cmd);
            case STEP_OUT -> executeStepOut(cmd);
            default -> log.trace("BDI: Unknown Type I command: {}", Integer.toHexString(cmd));
        }

        constrainTrackValues();
        nextEventTStates = currentTStates + TIMING_STEP_DELAY;
    }

    private void executeRestore() {
        log.trace("BDI: Command RESTORE");
        regTrack = 0;
        getSelectedDrive().setPhysicalTrack(0);
        lastStepDir = STEP_OUT;
    }

    private void executeSeek() {
        log.trace("BDI: Command SEEK to T:{}", regData);
        regTrack = regData;
        getSelectedDrive().setPhysicalTrack(regTrack);
    }

    private void executeStep(int cmd) {
        log.trace("BDI: Command STEP");
        if ((cmd & CMD_UPDATE_TRACK) != 0) {
            regTrack += lastStepDir;
        }
        getSelectedDrive().deltaPhysicalTrack(lastStepDir);
    }

    private void executeStepIn(int cmd) {
        log.trace("BDI: Command STEP IN");
        lastStepDir = STEP_IN;
        if ((cmd & CMD_UPDATE_TRACK) != 0) {
            regTrack += lastStepDir;
        }
        getSelectedDrive().deltaPhysicalTrack(lastStepDir);
    }

    private void executeStepOut(int cmd) {
        log.trace("BDI: Command STEP OUT");
        lastStepDir = STEP_OUT;
        if ((cmd & CMD_UPDATE_TRACK) != 0) {
            regTrack += lastStepDir;
        }
        getSelectedDrive().deltaPhysicalTrack(lastStepDir);
    }

    private void constrainTrackValues() {
        regTrack = Math.max(TRACK_MIN, Math.min(TRACK_MAX, regTrack));

        VirtualDrive drive = getSelectedDrive();
        int physicalTrack = drive.getPhysicalTrack();
        physicalTrack = Math.max(TRACK_MIN, Math.min(TRACK_MAX, physicalTrack));
        drive.setPhysicalTrack(physicalTrack);
    }

    private void executeTypeIIOrIIICommand(int cmd, CommandType cmdType) {
        if (cmdType.requiresDiskReady() && !isDiskReady()) {
            log.warn("BDI: Command {} failed: Not Ready (Motor:{}, Disk:{})",
                    Integer.toHexString(cmd), motorOn, getSelectedDrive().isHasDisk());
            finalizeCommand(STATUS_NOT_READY);
            return;
        }

        switch (cmdType) {
            case READ_SECTOR -> executeReadSector();
            case WRITE_SECTOR -> executeWriteSector();
            case READ_ADDRESS -> executeReadAddress();
            case READ_TRACK -> executeReadTrack();
            case WRITE_TRACK -> executeWriteTrack();
            default -> executeUnknownCommand(cmd);
        }
    }

    private void executeReadSector() {
        dataPos = 0;
        nextEventTStates = currentTStates + TIMING_SECTOR_SEARCH;
        log.trace("BDI: Command READ SECTOR T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
    }

    private void executeWriteSector() {
        dataPos = 0;
        nextEventTStates = currentTStates + TIMING_SECTOR_SEARCH;
        log.trace("BDI: Command WRITE SECTOR T:{}, S:{}, Side:{}", regTrack, regSector, currentSide);
    }

    private void executeReadAddress() {
        dataPos = 0;
        nextEventTStates = currentTStates + TIMING_READ_ADDRESS;
        log.trace("BDI: Command READ ADDRESS");
    }

    private void executeReadTrack() {
        dataPos = 0;
        nextEventTStates = currentTStates + TIMING_READ_TRACK;
        log.trace("BDI: Command READ TRACK");
    }

    private void executeWriteTrack() {
        // For TRD images formatting is not required - they are pre-formatted
        // Complete immediately as many emulators do
        log.trace("BDI: Command WRITE TRACK (Format) - completing immediately (TRD pre-formatted)");
        finalizeCommand(0);
    }

    private void executeUnknownCommand(int cmd) {
        nextEventTStates = currentTStates + TIMING_STEP_DELAY;
        log.trace("BDI: Command UNKNOWN: {}", Integer.toHexString(cmd));
    }

    private boolean isDiskReady() {
        return getSelectedDrive().isHasDisk() && motorOn;
    }

    private void finalizeCommand(int statusFlags) {
        log.trace("BDI: Finalize Command: {}, Status: {}",
                Integer.toHexString(commandReg), Integer.toHexString(statusFlags));
        regStatus = statusFlags;
        intrq = true;
        drq = false;
        currentState = ControllerState.IDLE;
    }

    // ========== ULA Integration ==========

    @Override
    public boolean initWithULA(Ula ula) {
        initTRDOS(ula);
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
        // Register input ports
        ula.addPortListener(PORT_CMD_STATUS, (InPortListener) this);
        ula.addPortListener(PORT_TRACK, (InPortListener) this);
        ula.addPortListener(PORT_SECTOR, (InPortListener) this);
        ula.addPortListener(PORT_DATA, (InPortListener) this);
        ula.addPortListener(PORT_SYSTEM, (InPortListener) this);

        // Register output ports
        ula.addPortListener(PORT_CMD_STATUS, (OutPortListener) this);
        ula.addPortListener(PORT_TRACK, (OutPortListener) this);
        ula.addPortListener(PORT_SECTOR, (OutPortListener) this);
        ula.addPortListener(PORT_DATA, (OutPortListener) this);
        ula.addPortListener(PORT_SYSTEM, (OutPortListener) this);
    }

    // ========== Utility Methods ==========

    private VirtualDrive getSelectedDrive() {
        return drives[selectedDriveIdx];
    }

    private boolean isValidDriveIndex(int drive) {
        return drive >= 0 && drive < DRIVE_COUNT && drives[drive] != null;
    }

    private boolean isValidOffset(int offset, VirtualDrive drive) {
        return offset >= 0 && offset < drive.dataSize();
    }

    private char getDriveLetter(int drive) {
        return (char) ('A' + drive);
    }

    private void logSystemPortAnswer(int sys) {
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
}
