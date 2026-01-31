package spectrum.jfx.hardware.disk.wd1793;

import lombok.experimental.UtilityClass;

/**
 * Constants for WD1793 (Soviet 1818ВГ93) Floppy Disk Controller.
 * <p>
 * This class contains all port addresses, status register flags, command masks,
 * and timing constants used by the WD1793 disk controller implementation.
 *
 * @see <a href="https://hansotten.file-hunter.com/technical-info/wd1793/">WD1793 Data Sheet</a>
 */
@UtilityClass
public final class WD1793Constants {

    // ========== Port Addresses ==========

    /**
     * Command register (write) / Status register (read)
     */
    public static final int PORT_CMD_STATUS = 0x1F;

    /**
     * Track register
     */
    public static final int PORT_TRACK = 0x3F;

    /**
     * Sector register
     */
    public static final int PORT_SECTOR = 0x5F;

    /**
     * Data register
     */
    public static final int PORT_DATA = 0x7F;

    /**
     * System port (Beta Disk Interface specific)
     */
    public static final int PORT_SYSTEM = 0xFF;

    // ========== Status Register Flags ==========

    /**
     * Bit 0: Controller is busy executing a command
     */
    public static final int STATUS_BUSY = 0x01;

    /**
     * Bit 1: Data Request - data register ready for read/write (Type II/III commands)
     */
    public static final int STATUS_DRQ = 0x02;

    /**
     * Bit 1: Index pulse detected (Type I commands only, same bit position as DRQ)
     */
    public static final int STATUS_INDEX = 0x02;

    /**
     * Bit 2: Head is positioned at track 0 (Type I commands only)
     */
    public static final int STATUS_TRACK0 = 0x04;

    /**
     * Bit 2: Lost Data - host didn't respond to DRQ in time (Type II/III commands)
     */
    public static final int STATUS_LOST_DATA = 0x04;

    /**
     * Bit 3: CRC error in data field
     */
    public static final int STATUS_CRC_ERROR = 0x08;

    /**
     * Bit 4: Record Not Found - sector ID not found
     */
    public static final int STATUS_RECORD_NOT_FOUND = 0x10;

    /**
     * Bit 5: Head is loaded and engaged (Type I commands only)
     */
    public static final int STATUS_HEAD_LOADED = 0x20;

    /**
     * Bit 5: Record type - deleted data mark detected (Type II/III commands)
     */
    public static final int STATUS_RECORD_TYPE = 0x20;

    /**
     * Bit 6: Disk is write protected
     */
    public static final int STATUS_WRITE_PROTECTED = 0x40;

    /**
     * Bit 7: Drive not ready (no disk or motor off)
     */
    public static final int STATUS_NOT_READY = 0x80;

    // ========== System Port Flags (Beta Disk Interface) ==========

    /**
     * Bits 0-1: Drive select (0-3)
     */
    public static final int SYS_DRIVE_MASK = 0x03;

    /**
     * Bit 2: Controller reset (active low)
     */
    public static final int SYS_RESET = 0x04;

    /**
     * Bit 3: Motor on
     */
    public static final int SYS_MOTOR = 0x08;

    /**
     * Bit 4: Side select (active low = side 1)
     */
    public static final int SYS_SIDE = 0x10;

    /**
     * Bit 6: DRQ status (read)
     */
    public static final int SYS_DRQ = 0x40;

    /**
     * Bit 7: INTRQ status (read)
     */
    public static final int SYS_INTRQ = 0x80;

    // ========== Command Masks ==========

    /**
     * Mask to distinguish command types (Type I: 0x00-0x7F, Type II/III: 0x80-0xFF)
     */
    public static final int CMD_TYPE_MASK = 0x80;

    /**
     * Mask for command group identification
     */
    public static final int CMD_GROUP_MASK = 0xF0;

    /**
     * Mask for Type II/III command group (3 MSB)
     */
    public static final int CMD_TYPE23_GROUP_MASK = 0xE0;

    /**
     * Multi-sector flag bit for Type II commands
     */
    public static final int CMD_MULTI_SECTOR = 0x10;

    /**
     * Update track register flag for Step commands
     */
    public static final int CMD_UPDATE_TRACK = 0x10;

    /**
     * Immediate interrupt bit for Force Interrupt command
     */
    public static final int CMD_IMMEDIATE_IRQ = 0x08;

    // ========== Command Codes ==========

    public static final int CMD_RESTORE = 0x00;
    public static final int CMD_SEEK = 0x10;
    public static final int CMD_STEP = 0x20;
    public static final int CMD_STEP_UPDATE = 0x30;
    public static final int CMD_STEP_IN = 0x40;
    public static final int CMD_STEP_IN_UPDATE = 0x50;
    public static final int CMD_STEP_OUT = 0x60;
    public static final int CMD_STEP_OUT_UPDATE = 0x70;
    public static final int CMD_READ_SECTOR = 0x80;
    public static final int CMD_WRITE_SECTOR = 0xA0;
    public static final int CMD_READ_ADDRESS = 0xC0;
    public static final int CMD_READ_TRACK = 0xE0;
    public static final int CMD_WRITE_TRACK = 0xF0;
    public static final int CMD_FORCE_INTERRUPT = 0xD0;

    // ========== Timing Constants (in T-states at 3.5MHz) ==========

    /**
     * DRQ timeout (~14ms) - time allowed for host to respond to DRQ.
     * Per WD1793 spec, Lost Data flag is set if DRQ not serviced in time.
     */
    public static final long TIMING_DRQ_TIMEOUT = 50_000L;

    /**
     * Byte transfer interval (~32µs for DD disk ≈ 112 T-states)
     */
    public static final long TIMING_BYTE_INTERVAL = 112L;

    /**
     * Head step/seek mechanical delay
     */
    public static final long TIMING_STEP_DELAY = 5_000L;

    /**
     * Sector search delay (~8.5ms)
     */
    public static final long TIMING_SECTOR_SEARCH = 30_000L;

    /**
     * Read Address command delay
     */
    public static final long TIMING_READ_ADDRESS = 15_000L;

    /**
     * Read Track command delay
     */
    public static final long TIMING_READ_TRACK = 50_000L;

    /**
     * Delay between sectors in multi-sector operations
     */
    public static final long TIMING_INTER_SECTOR = 1_000L;

    /**
     * Index pulse period (~200ms at 300RPM ≈ 700,000 T-states)
     */
    public static final long TIMING_INDEX_PERIOD = 700_000L;

    /**
     * Index pulse duration (~4ms)
     */
    public static final long TIMING_INDEX_PULSE = 14_000L;

    // ========== Track Limits ==========

    /**
     * Minimum track number
     */
    public static final int TRACK_MIN = 0;

    /**
     * Maximum track number
     */
    public static final int TRACK_MAX = 160;

    /**
     * Default initial sector number
     */
    public static final int DEFAULT_SECTOR = 1;

    /**
     * Step direction: towards track 0
     */
    public static final int STEP_OUT = -1;

    /**
     * Step direction: away from track 0
     */
    public static final int STEP_IN = 1;
}
