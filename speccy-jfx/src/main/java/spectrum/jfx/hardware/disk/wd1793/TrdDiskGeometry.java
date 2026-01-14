package spectrum.jfx.hardware.disk.wd1793;

import lombok.experimental.UtilityClass;

/**
 * Defines the disk geometry for TR-DOS TRD format disks.
 * <p>
 * TRD format uses a specific layout:
 * <ul>
 *   <li>80 cylinders (tracks 0-79)</li>
 *   <li>2 sides per cylinder</li>
 *   <li>16 sectors per track</li>
 *   <li>256 bytes per sector</li>
 * </ul>
 * <p>
 * Track interleaving: Track 0 Side 0, Track 0 Side 1, Track 1 Side 0, Track 1 Side 1, etc.
 */
@UtilityClass
public final class TrdDiskGeometry {

    /**
     * Number of sides per disk
     */
    public static final int SIDES = 2;

    /**
     * Number of sectors per track
     */
    public static final int SECTORS_PER_TRACK = 16;

    /**
     * Bytes per sector
     */
    public static final int BYTES_PER_SECTOR = 256;

    /**
     * Maximum valid sector number (1-based)
     */
    public static final int MAX_SECTOR = 16;

    /**
     * Bytes per track (one side)
     */
    public static final int BYTES_PER_TRACK = SECTORS_PER_TRACK * BYTES_PER_SECTOR;

    /**
     * Approximate raw track size for Write Track command
     */
    public static final int RAW_TRACK_SIZE = 6250;

    /**
     * Sector size code for 256-byte sectors (used in Read Address)
     */
    public static final int SECTOR_SIZE_CODE = 1;

    /**
     * Number of bytes in Read Address response
     */
    public static final int ADDRESS_FIELD_SIZE = 6;

    /**
     * Calculates the absolute byte offset for a given track, side, and sector.
     *
     * @param track  logical track number (0-79)
     * @param side   side number (0 or 1)
     * @param sector sector number (1-16, 1-based)
     * @return absolute byte offset in disk image
     */
    public static int calculateOffset(int track, int side, int sector) {
        int trackOffset = (track * SIDES + side) * BYTES_PER_TRACK;
        int sectorOffset = (sector - 1) * BYTES_PER_SECTOR;
        return trackOffset + sectorOffset;
    }

    /**
     * Calculates the absolute byte offset for track start.
     *
     * @param track logical track number (0-79)
     * @param side  side number (0 or 1)
     * @return absolute byte offset in disk image
     */
    public static int calculateTrackOffset(int track, int side) {
        return (track * SIDES + side) * BYTES_PER_TRACK;
    }
}
