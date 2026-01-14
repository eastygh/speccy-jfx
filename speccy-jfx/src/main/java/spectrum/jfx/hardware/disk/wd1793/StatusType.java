package spectrum.jfx.hardware.disk.wd1793;

/**
 * Determines how the status register bits are interpreted.
 * <p>
 * The WD1793 status register uses the same bit positions for different meanings
 * depending on the command type being executed.
 */
public enum StatusType {

    /**
     * Type I commands (Restore, Seek, Step).
     * <p>
     * Status bits interpretation:
     * <ul>
     *   <li>Bit 1: Index pulse</li>
     *   <li>Bit 2: Track 0</li>
     *   <li>Bit 5: Head loaded</li>
     * </ul>
     */
    TYPE_1,

    /**
     * Type II/III commands (Read/Write Sector, Read/Write Track, Read Address).
     * <p>
     * Status bits interpretation:
     * <ul>
     *   <li>Bit 1: Data Request (DRQ)</li>
     *   <li>Bit 2: Lost Data</li>
     *   <li>Bit 5: Record Type (deleted data mark)</li>
     * </ul>
     */
    TYPE_2
}
