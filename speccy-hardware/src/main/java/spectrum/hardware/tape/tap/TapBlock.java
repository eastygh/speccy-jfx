package spectrum.hardware.tape.tap;

import lombok.Getter;

import java.util.Arrays;

import static spectrum.hardware.tape.TapeConstants.*;


/**
 * Represents a single TAP block with its data and metadata.
 * <p>
 * TAP file format stores blocks as:
 * - 2 bytes: block length (little-endian)
 * - 1 byte: flag (0x00 for header, 0xFF for data)
 * - N bytes: payload
 * - 1 byte: checksum (XOR of all bytes including flag)
 */
@Getter
public class TapBlock {

    private final int flagByte;
    private final byte[] data;      // Complete data including flag and checksum
    private final boolean isHeader;
    private final boolean checksumValid;

    /**
     * Creates a TAP block from recorded data.
     *
     * @param data Complete block data including flag and checksum
     */
    public TapBlock(byte[] data) {
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("TAP block must have at least 2 bytes (flag + checksum)");
        }

        this.data = Arrays.copyOf(data, data.length);
        this.flagByte = data[0] & 0xFF;
        this.isHeader = flagByte == TAP_FLAG_HEADER;
        this.checksumValid = verifyChecksum();
    }

    /**
     * Creates a TAP block with explicit flag and payload.
     *
     * @param flagByte Block flag (0x00 for header, 0xFF for data)
     * @param payload  Block payload without flag and checksum
     */
    public TapBlock(int flagByte, byte[] payload) {
        this.flagByte = flagByte & 0xFF;
        this.isHeader = this.flagByte == TAP_FLAG_HEADER;

        // Construct full data: flag + payload + checksum
        this.data = new byte[payload.length + 2];
        this.data[0] = (byte) this.flagByte;
        System.arraycopy(payload, 0, this.data, 1, payload.length);
        this.data[this.data.length - 1] = (byte) calculateChecksum();
        this.checksumValid = true;
    }

    /**
     * Gets the payload data (excluding flag and checksum).
     *
     * @return Payload bytes
     */
    public byte[] getPayload() {
        if (data.length <= 2) {
            return new byte[0];
        }
        return Arrays.copyOfRange(data, 1, data.length - 1);
    }

    /**
     * Gets the block length as it would appear in TAP file.
     * This is the total length including flag and checksum.
     *
     * @return Block length in bytes
     */
    public int getBlockLength() {
        return data.length;
    }

    /**
     * Gets the stored checksum byte.
     *
     * @return Checksum value
     */
    public int getStoredChecksum() {
        return data[data.length - 1] & 0xFF;
    }

    /**
     * Calculates the expected checksum.
     *
     * @return Calculated checksum
     */
    public int calculateChecksum() {
        int xor = 0;
        for (int i = 0; i < data.length - 1; i++) {
            xor ^= (data[i] & 0xFF);
        }
        return xor;
    }

    /**
     * Verifies that the stored checksum matches calculated.
     *
     * @return true if checksum is valid
     */
    private boolean verifyChecksum() {
        return getStoredChecksum() == calculateChecksum();
    }

    /**
     * Converts the block to TAP file format bytes.
     * Includes the 2-byte length prefix.
     *
     * @return TAP format bytes
     */
    public byte[] toTapBytes() {
        byte[] result = new byte[data.length + 2];
        // Length in little-endian
        result[0] = (byte) (data.length & 0xFF);
        result[1] = (byte) ((data.length >> 8) & 0xFF);
        System.arraycopy(data, 0, result, 2, data.length);
        return result;
    }

    /**
     * Gets block type description.
     *
     * @return Human-readable block type
     */
    public String getTypeDescription() {
        if (!isHeader) {
            return "Data block";
        }
        if (data.length < 2) {
            return "Invalid header";
        }
        int headerType = data[1] & 0xFF;
        return switch (headerType) {
            case HEADER_TYPE_PROGRAM -> "Program header";
            case HEADER_TYPE_NUMBER_ARRAY -> "Number array header";
            case HEADER_TYPE_CHAR_ARRAY -> "Character array header";
            case HEADER_TYPE_CODE -> "Code header";
            default -> "Unknown header type " + headerType;
        };
    }

    /**
     * Gets the filename from a header block.
     *
     * @return Filename or null if not a header
     */
    public String getFilename() {
        if (!isHeader || data.length < 12) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < 12; i++) {
            char c = (char) (data[i] & 0xFF);
            if (c >= 32 && c < 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        String name = getFilename();
        return String.format("TapBlock[%s%s, %d bytes, checksum %s]",
                getTypeDescription(),
                name != null ? " \"" + name + "\"" : "",
                data.length,
                checksumValid ? "OK" : "FAIL");
    }
}
