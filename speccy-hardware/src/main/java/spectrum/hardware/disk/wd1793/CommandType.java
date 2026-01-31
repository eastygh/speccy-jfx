package spectrum.hardware.disk.wd1793;

import static spectrum.hardware.disk.wd1793.WD1793Constants.*;

/**
 * Enumerates all WD1793 command types with their identifying masks and properties.
 */
public enum CommandType {

    // Type I Commands (positioning)
    RESTORE(CMD_RESTORE, CMD_GROUP_MASK, StatusType.TYPE_1, false),
    SEEK(CMD_SEEK, CMD_GROUP_MASK, StatusType.TYPE_1, false),
    STEP(CMD_STEP, CMD_TYPE23_GROUP_MASK, StatusType.TYPE_1, false),
    STEP_IN(CMD_STEP_IN, CMD_TYPE23_GROUP_MASK, StatusType.TYPE_1, false),
    STEP_OUT(CMD_STEP_OUT, CMD_TYPE23_GROUP_MASK, StatusType.TYPE_1, false),

    // Type II Commands (sector operations)
    READ_SECTOR(CMD_READ_SECTOR, CMD_TYPE23_GROUP_MASK, StatusType.TYPE_2, true),
    WRITE_SECTOR(CMD_WRITE_SECTOR, CMD_TYPE23_GROUP_MASK, StatusType.TYPE_2, true),

    // Type III Commands (track/address operations)
    READ_ADDRESS(CMD_READ_ADDRESS, CMD_GROUP_MASK, StatusType.TYPE_2, true),
    READ_TRACK(CMD_READ_TRACK, CMD_GROUP_MASK, StatusType.TYPE_2, true),
    WRITE_TRACK(CMD_WRITE_TRACK, CMD_GROUP_MASK, StatusType.TYPE_2, true),

    // Type IV Command (interrupt)
    FORCE_INTERRUPT(CMD_FORCE_INTERRUPT, CMD_GROUP_MASK, StatusType.TYPE_1, false),

    // Unknown command
    UNKNOWN(0xFF, 0x00, StatusType.TYPE_1, false);

    private final int code;
    private final int mask;
    private final StatusType statusType;
    private final boolean requiresDiskReady;

    CommandType(int code, int mask, StatusType statusType, boolean requiresDiskReady) {
        this.code = code;
        this.mask = mask;
        this.statusType = statusType;
        this.requiresDiskReady = requiresDiskReady;
    }

    /**
     * Identifies the command type from a raw command byte.
     *
     * @param cmd the command byte written to the command register
     * @return the identified CommandType
     */
    public static CommandType fromCommand(int cmd) {
        // Force Interrupt is special - check first
        if ((cmd & CMD_GROUP_MASK) == CMD_FORCE_INTERRUPT) {
            return FORCE_INTERRUPT;
        }

        // Type I commands (bit 7 = 0)
        if ((cmd & CMD_TYPE_MASK) == 0) {
            int group = cmd & CMD_GROUP_MASK;
            return switch (group) {
                case CMD_RESTORE -> RESTORE;
                case CMD_SEEK -> SEEK;
                case CMD_STEP, CMD_STEP_UPDATE -> STEP;
                case CMD_STEP_IN, CMD_STEP_IN_UPDATE -> STEP_IN;
                case CMD_STEP_OUT, CMD_STEP_OUT_UPDATE -> STEP_OUT;
                default -> UNKNOWN;
            };
        }

        // Type II/III commands (bit 7 = 1)
        int group23 = cmd & CMD_TYPE23_GROUP_MASK;
        return switch (group23) {
            case CMD_READ_SECTOR -> READ_SECTOR;
            case CMD_WRITE_SECTOR -> WRITE_SECTOR;
            default -> {
                int group = cmd & CMD_GROUP_MASK;
                yield switch (group) {
                    case CMD_READ_ADDRESS -> READ_ADDRESS;
                    case CMD_READ_TRACK -> READ_TRACK;
                    case CMD_WRITE_TRACK -> WRITE_TRACK;
                    default -> UNKNOWN;
                };
            }
        };
    }

    /**
     * @return true if this is a Type I (positioning) command
     */
    public boolean isTypeI() {
        return statusType == StatusType.TYPE_1 && this != FORCE_INTERRUPT;
    }

    /**
     * @return true if this command requires the disk to be ready and motor on
     */
    public boolean requiresDiskReady() {
        return requiresDiskReady;
    }

    /**
     * @return the status type used when this command is active
     */
    public StatusType getStatusType() {
        return statusType;
    }

    /**
     * @return the base command code
     */
    public int getCode() {
        return code;
    }
}
