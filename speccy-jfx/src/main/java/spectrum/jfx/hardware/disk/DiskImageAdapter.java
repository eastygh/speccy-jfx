package spectrum.jfx.hardware.disk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskImageAdapter {

    private static final String SCL_SIGNATURE = "SINCLAIR";
    private static final int TRD_SIZE = 640 * 1024;
    private static final int SECTOR_SIZE = 256;

    // TR-DOS system sector is Track 0, Sector 9.
    // In 0-indexed offset: 8 * 256 = 2048 (0x800)
    private static final int SYSTEM_SECTOR_OFFSET = 0x800;

    // Data usually starts at Track 1, Sector 1 (Offset 256 * 16 = 4096)
    private static final int DATA_START_OFFSET = 0x1000;

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

        // If it's a small file or unknown, pad it to TRD size
        byte[] trd = new byte[TRD_SIZE];
        System.arraycopy(sourceData, 0, trd, 0, Math.min(sourceData.length, TRD_SIZE));
        return trd;
    }

    private static boolean isScl(byte[] data) {
        if (data.length < 9) return false;
        return SCL_SIGNATURE.equals(new String(data, 0, 8));
    }

    private static byte[] transformSclToTrd(byte[] scl) {
        byte[] trd = new byte[TRD_SIZE];
        int numFiles = scl[8] & 0xFF;

        // 1. Checksum validation (optional but good for logs)
        validateSclChecksum(scl);

        // 2. Copy file headers (Max 128 files in TR-DOS)
        int sclPtr = 9;
        for (int i = 0; i < numFiles && i < 128; i++) {
            System.arraycopy(scl, sclPtr, trd, i * 16, 14);
            sclPtr += 14;
        }

        // 3. Calculate data size
        int totalSectors = 0;
        int scanPtr = 9;
        for (int i = 0; i < numFiles; i++) {
            totalSectors += scl[scanPtr + 13] & 0xFF;
            scanPtr += 14;
        }

        // 4. Setup System Sector (Track 0, Sector 9)
        // Offset 0x800 in the TRD file
        trd[SYSTEM_SECTOR_OFFSET + 225] = (byte) (totalSectors % 16);       // Next free sector
        trd[SYSTEM_SECTOR_OFFSET + 226] = (byte) ((totalSectors / 16) + 1); // Next free track (start from 1)
        trd[SYSTEM_SECTOR_OFFSET + 227] = 0x10;                             // 80 tracks, double sided
        trd[SYSTEM_SECTOR_OFFSET + 228] = (byte) numFiles;

        int freeSectors = 2544 - totalSectors;
        trd[SYSTEM_SECTOR_OFFSET + 229] = (byte) (freeSectors & 0xFF);
        trd[SYSTEM_SECTOR_OFFSET + 230] = (byte) (freeSectors >> 8);
        trd[SYSTEM_SECTOR_OFFSET + 231] = 0x10;                             // TR-DOS ID (0x10)

        // 5. Copy file data starting from Track 1
        int sclDataPtr = 9 + (numFiles * 14);
        int trdDataPtr = DATA_START_OFFSET;
        int dataToCopy = Math.min(totalSectors * SECTOR_SIZE, scl.length - sclDataPtr - 4);

        if (dataToCopy > 0) {
            System.arraycopy(scl, sclDataPtr, trd, trdDataPtr, dataToCopy);
        }

        return trd;
    }

    private static byte[] createEmptyTrd() {
        byte[] trd = new byte[TRD_SIZE];
        // Minimal system sector for an empty disk
        trd[SYSTEM_SECTOR_OFFSET + 225] = 0x01; // First free sector
        trd[SYSTEM_SECTOR_OFFSET + 226] = 0x01; // First free track
        trd[SYSTEM_SECTOR_OFFSET + 227] = 0x10;
        trd[SYSTEM_SECTOR_OFFSET + 229] = (byte) (2544 & 0xFF);
        trd[SYSTEM_SECTOR_OFFSET + 230] = (byte) (2544 >> 8);
        trd[SYSTEM_SECTOR_OFFSET + 231] = 0x10;
        return trd;
    }

    private static void validateSclChecksum(byte[] scl) {
        if (scl.length < 4) return;
        int calculated = 0;
        for (int i = 0; i < scl.length - 4; i++) {
            calculated += (scl[i] & 0xFF);
        }
        // Logs could be added here if calculated != stored checksum
    }
}