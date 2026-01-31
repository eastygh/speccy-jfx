package spectrum.jfx.hardware.disk;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter for converting various disk image formats to TRD format.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>TRD - native TR-DOS format (640KB)</li>
 *   <li>SCL - compressed TR-DOS format</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class DiskImageAdapter {

    private static final String SCL_SIGNATURE = "SINCLAIR";
    private static final int TRD_SIZE = 640 * 1024;
    private static final int SECTOR_SIZE = 256;
    private static final int SECTORS_PER_TRACK = 16;

    // TRD catalog: Track 0, Sectors 1-8 (128 entries × 16 bytes = 2048 bytes)
    private static final int CATALOG_ENTRY_SIZE = 16;
    private static final int MAX_CATALOG_ENTRIES = 128;

    // SCL file header size (without starting sector/track)
    private static final int SCL_HEADER_SIZE = 14;

    // TR-DOS system sector is Track 0, Sector 9 (offset 8 × 256 = 2048)
    private static final int SYSTEM_SECTOR_OFFSET = 8 * SECTOR_SIZE;

    // Data starts at Track 1, Sector 1 (offset 16 × 256 = 4096)
    // Track 0 contains: Sectors 1-8 (catalog) + Sector 9 (system) + Sectors 10-16 (reserved)
    private static final int DATA_START_TRACK = 1;
    private static final int DATA_START_SECTOR = 0;

    // Total sectors on 80-track double-sided disk: 80 × 2 × 16 = 2560
    // System track (Track 0) uses 16 sectors, leaving 2560 - 16 = 2544 for data
    private static final int TOTAL_DATA_SECTORS = 2544;

    // System sector field offsets (relative to system sector start)
    private static final int SYS_NEXT_FREE_SECTOR = 225;
    private static final int SYS_NEXT_FREE_TRACK = 226;
    private static final int SYS_DISK_TYPE = 227;
    private static final int SYS_FILE_COUNT = 228;
    private static final int SYS_FREE_SECTORS_LO = 229;
    private static final int SYS_FREE_SECTORS_HI = 230;
    private static final int SYS_TRDOS_ID = 231;

    // Disk type: 0x16 = 80 tracks, double-sided
    private static final byte DISK_TYPE_DS80 = 0x16;
    // TR-DOS identification byte
    private static final byte TRDOS_ID = 0x10;

    public static byte[] convertToTrd(byte[] sourceData) {
        if (sourceData == null || sourceData.length == 0) {
            return createEmptyTrd();
        }

        if (isScl(sourceData)) {
            log.info("SCL format detected, converting to TRD...");
            return transformSclToTrd(sourceData);
        }

        if (sourceData.length == TRD_SIZE) {
            return sourceData;
        }

        // Unknown format - wrap in TRD container
        byte[] trd = createEmptyTrd();
        System.arraycopy(sourceData, 0, trd, 0, Math.min(sourceData.length, TRD_SIZE));
        return trd;
    }

    public static boolean isScl(byte[] data) {
        if (data.length < 9) return false;
        return SCL_SIGNATURE.equals(new String(data, 0, 8));
    }

    /**
     * Converts SCL format to TRD format.
     * <p>
     * SCL format structure:
     * <ul>
     *   <li>8 bytes: "SINCLAIR" signature</li>
     *   <li>1 byte: number of files</li>
     *   <li>N × 14 bytes: file headers (name[8], type[1], start[2], length[2], sectors[1])</li>
     *   <li>File data blocks (256 bytes per sector)</li>
     *   <li>4 bytes: checksum</li>
     * </ul>
     * <p>
     * TRD catalog entry structure (16 bytes):
     * <ul>
     *   <li>8 bytes: filename</li>
     *   <li>1 byte: file type</li>
     *   <li>2 bytes: start address / parameters</li>
     *   <li>2 bytes: length in bytes</li>
     *   <li>1 byte: length in sectors</li>
     *   <li>1 byte: starting sector (0-15)</li>
     *   <li>1 byte: starting track</li>
     * </ul>
     */
    private static byte[] transformSclToTrd(byte[] scl) {
        byte[] trd = new byte[TRD_SIZE];
        int numFiles = scl[8] & 0xFF;

        if (numFiles > MAX_CATALOG_ENTRIES) {
            log.warn("SCL contains {} files, truncating to {}", numFiles, MAX_CATALOG_ENTRIES);
            numFiles = MAX_CATALOG_ENTRIES;
        }

        validateSclChecksum(scl);

        // Track current position for file data placement
        int currentTrack = DATA_START_TRACK;
        int currentSector = DATA_START_SECTOR;
        int totalSectorsUsed = 0;

        // Process each file: copy header and calculate position
        int sclHeaderPtr = 9;  // After signature and file count
        int sclDataPtr = 9 + (numFiles * SCL_HEADER_SIZE);  // Start of file data in SCL
        int trdDataOffset = currentTrack * SECTORS_PER_TRACK * SECTOR_SIZE;

        for (int i = 0; i < numFiles; i++) {
            int catalogOffset = i * CATALOG_ENTRY_SIZE;

            // Copy 14 bytes from SCL header to TRD catalog
            System.arraycopy(scl, sclHeaderPtr, trd, catalogOffset, SCL_HEADER_SIZE);

            // Get file size in sectors
            int fileSectors = scl[sclHeaderPtr + 13] & 0xFF;

            // Set starting sector and track (bytes 14-15 of TRD catalog entry)
            trd[catalogOffset + 14] = (byte) currentSector;
            trd[catalogOffset + 15] = (byte) currentTrack;

            // Copy file data to TRD
            int fileDataSize = fileSectors * SECTOR_SIZE;
            int srcAvailable = scl.length - sclDataPtr - 4;  // -4 for checksum
            int bytesToCopy = Math.min(fileDataSize, srcAvailable);

            if (bytesToCopy > 0 && trdDataOffset + bytesToCopy <= TRD_SIZE) {
                System.arraycopy(scl, sclDataPtr, trd, trdDataOffset, bytesToCopy);
            }

            // Advance pointers
            sclHeaderPtr += SCL_HEADER_SIZE;
            sclDataPtr += fileDataSize;
            trdDataOffset += fileDataSize;
            totalSectorsUsed += fileSectors;

            // Calculate next file position
            currentSector += fileSectors;
            while (currentSector >= SECTORS_PER_TRACK) {
                currentSector -= SECTORS_PER_TRACK;
                currentTrack++;
            }
        }

        // Setup system sector (Track 0, Sector 9)
        setupSystemSector(trd, numFiles, currentTrack, currentSector, totalSectorsUsed);

        log.info("SCL converted: {} files, {} sectors used", numFiles, totalSectorsUsed);
        return trd;
    }

    private static void setupSystemSector(byte[] trd, int numFiles, int nextTrack, int nextSector, int usedSectors) {
        // Next free sector (0-15)
        trd[SYSTEM_SECTOR_OFFSET + SYS_NEXT_FREE_SECTOR] = (byte) nextSector;
        // Next free track
        trd[SYSTEM_SECTOR_OFFSET + SYS_NEXT_FREE_TRACK] = (byte) nextTrack;
        // Disk type: 80 tracks, double-sided
        trd[SYSTEM_SECTOR_OFFSET + SYS_DISK_TYPE] = DISK_TYPE_DS80;
        // Number of files
        trd[SYSTEM_SECTOR_OFFSET + SYS_FILE_COUNT] = (byte) numFiles;

        // Free sectors count
        int freeSectors = TOTAL_DATA_SECTORS - usedSectors;
        trd[SYSTEM_SECTOR_OFFSET + SYS_FREE_SECTORS_LO] = (byte) (freeSectors & 0xFF);
        trd[SYSTEM_SECTOR_OFFSET + SYS_FREE_SECTORS_HI] = (byte) ((freeSectors >> 8) & 0xFF);

        // TR-DOS identification byte
        trd[SYSTEM_SECTOR_OFFSET + SYS_TRDOS_ID] = TRDOS_ID;

        // Fill disk title area with spaces (offset 245-254)
        for (int i = 245; i <= 254; i++) {
            trd[SYSTEM_SECTOR_OFFSET + i] = 0x20;
        }
    }

    private static byte[] createEmptyTrd() {
        byte[] trd = new byte[TRD_SIZE];
        // Setup system sector for empty disk
        setupSystemSector(trd, 0, DATA_START_TRACK, DATA_START_SECTOR, 0);
        return trd;
    }

    private static void validateSclChecksum(byte[] scl) {
        if (scl.length < 4) return;

        int calculated = 0;
        for (int i = 0; i < scl.length - 4; i++) {
            calculated += (scl[i] & 0xFF);
        }

        // Read stored checksum (last 4 bytes, little-endian 32-bit)
        int stored = (scl[scl.length - 4] & 0xFF)
                | ((scl[scl.length - 3] & 0xFF) << 8)
                | ((scl[scl.length - 2] & 0xFF) << 16)
                | ((scl[scl.length - 1] & 0xFF) << 24);

        if (calculated != stored) {
            log.warn("SCL checksum mismatch: calculated={}, stored={}", calculated, stored);
        }
    }

    /**
     * Converts TRD format to SCL format.
     * <p>
     * SCL is a compressed format that stores only the catalog entries and file data,
     * without the empty space typical in TRD images.
     *
     * @param trd the TRD disk image data
     * @return SCL format data (empty SCL if input is invalid or empty)
     */
    public static byte[] convertToScl(byte[] trd) {
        if (trd == null || trd.length < SYSTEM_SECTOR_OFFSET + 256) {
            log.error("Invalid TRD data: too small or null");
            return createEmptyScl();
        }

        // Count files and calculate total sectors
        int numFiles = countTrdFiles(trd);
        if (numFiles == 0) {
            log.info("TRD image is empty, creating empty SCL");
            return createEmptyScl();
        }

        int totalSectors = calculateTotalSectors(trd, numFiles);

        // Calculate SCL size: signature(8) + count(1) + headers(N×14) + data(sectors×256) + checksum(4)
        int sclSize = 8 + 1 + (numFiles * SCL_HEADER_SIZE) + (totalSectors * SECTOR_SIZE) + 4;
        byte[] scl = new byte[sclSize];

        // Write signature
        System.arraycopy(SCL_SIGNATURE.getBytes(), 0, scl, 0, 8);

        // Write file count
        scl[8] = (byte) numFiles;

        // Write file headers and collect data
        int sclHeaderPtr = 9;
        int sclDataPtr = 9 + (numFiles * SCL_HEADER_SIZE);

        for (int i = 0; i < numFiles; i++) {
            int catalogOffset = i * CATALOG_ENTRY_SIZE;

            // Check if this is a valid file entry (first byte != 0x00)
            if (trd[catalogOffset] == 0x00) {
                continue;
            }

            // Copy 14 bytes of header (without sector/track)
            System.arraycopy(trd, catalogOffset, scl, sclHeaderPtr, SCL_HEADER_SIZE);

            // Get file location and size from TRD catalog
            int fileSectors = trd[catalogOffset + 13] & 0xFF;
            int startSector = trd[catalogOffset + 14] & 0xFF;
            int startTrack = trd[catalogOffset + 15] & 0xFF;

            // Calculate TRD data offset and copy file data
            int trdDataOffset = (startTrack * SECTORS_PER_TRACK + startSector) * SECTOR_SIZE;
            int fileDataSize = fileSectors * SECTOR_SIZE;

            if (trdDataOffset + fileDataSize <= trd.length && sclDataPtr + fileDataSize <= sclSize - 4) {
                System.arraycopy(trd, trdDataOffset, scl, sclDataPtr, fileDataSize);
            } else {
                log.warn("File {} data out of bounds: track={}, sector={}, sectors={}",
                        i, startTrack, startSector, fileSectors);
            }

            sclHeaderPtr += SCL_HEADER_SIZE;
            sclDataPtr += fileDataSize;
        }

        // Calculate and write checksum
        int checksum = calculateSclChecksum(scl, sclSize - 4);
        scl[sclSize - 4] = (byte) (checksum & 0xFF);
        scl[sclSize - 3] = (byte) ((checksum >> 8) & 0xFF);
        scl[sclSize - 2] = (byte) ((checksum >> 16) & 0xFF);
        scl[sclSize - 1] = (byte) ((checksum >> 24) & 0xFF);

        log.info("TRD converted to SCL: {} files, {} sectors, {} bytes", numFiles, totalSectors, sclSize);
        return scl;
    }

    /**
     * Counts the number of valid files in a TRD catalog.
     * A valid file entry has a non-zero first byte (filename start).
     */
    private static int countTrdFiles(byte[] trd) {
        int count = 0;
        for (int i = 0; i < MAX_CATALOG_ENTRIES; i++) {
            int offset = i * CATALOG_ENTRY_SIZE;
            // First byte of filename: 0x00 = empty entry, 0x01 = deleted file
            if (trd[offset] != 0x00 && trd[offset] != 0x01) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculates total sectors used by all files in TRD catalog.
     */
    private static int calculateTotalSectors(byte[] trd, int numFiles) {
        int total = 0;
        int filesFound = 0;
        for (int i = 0; i < MAX_CATALOG_ENTRIES && filesFound < numFiles; i++) {
            int offset = i * CATALOG_ENTRY_SIZE;
            if (trd[offset] != 0x00 && trd[offset] != 0x01) {
                total += trd[offset + 13] & 0xFF;
                filesFound++;
            }
        }
        return total;
    }

    /**
     * Calculates SCL checksum (sum of all bytes before checksum).
     */
    private static int calculateSclChecksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += data[i] & 0xFF;
        }
        return sum;
    }

    /**
     * Creates an empty SCL file (no files).
     */
    private static byte[] createEmptyScl() {
        // Empty SCL: signature(8) + count(1) + checksum(4) = 13 bytes
        byte[] scl = new byte[13];
        System.arraycopy(SCL_SIGNATURE.getBytes(), 0, scl, 0, 8);
        scl[8] = 0; // No files

        int checksum = calculateSclChecksum(scl, 9);
        scl[9] = (byte) (checksum & 0xFF);
        scl[10] = (byte) ((checksum >> 8) & 0xFF);
        scl[11] = (byte) ((checksum >> 16) & 0xFF);
        scl[12] = (byte) ((checksum >> 24) & 0xFF);

        return scl;
    }
}
