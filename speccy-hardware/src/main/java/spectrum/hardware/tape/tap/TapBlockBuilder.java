package spectrum.hardware.tape.tap;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static spectrum.hardware.tape.TapeConstants.*;

/**
 * Builder for creating TAP blocks incrementally.
 * <p>
 * Accumulates bytes during recording and produces
 * a complete TapBlock when finished.
 */
public class TapBlockBuilder {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean hasFlag = false;
    private int flagByte = -1;

    /**
     * Adds a byte to the block.
     *
     * @param value Byte value (0-255)
     */
    public void addByte(int value) {
        if (!hasFlag) {
            flagByte = value & 0xFF;
            hasFlag = true;
        }
        buffer.write(value & 0xFF);
    }

    /**
     * Gets the current number of bytes accumulated.
     *
     * @return Byte count
     */
    public int getByteCount() {
        return buffer.size();
    }

    /**
     * Gets the flag byte if available.
     *
     * @return Flag byte or -1 if not yet received
     */
    public int getFlagByte() {
        return flagByte;
    }

    /**
     * Checks if this appears to be a header block.
     *
     * @return true if flag byte is 0x00
     */
    public boolean isHeader() {
        return flagByte == TAP_FLAG_HEADER;
    }

    /**
     * Gets the expected total length for standard blocks.
     * Headers are 19 bytes (flag + 17 byte header + checksum).
     *
     * @return Expected length or -1 if unknown
     */
    public int getExpectedLength() {
        if (flagByte == TAP_FLAG_HEADER) {
            return TAP_HEADER_LENGTH;
        }
        return -1;  // Data blocks have variable length
    }

    /**
     * Checks if the block appears complete.
     * For headers, checks against standard header length.
     *
     * @return true if block seems complete
     */
    public boolean isComplete() {
        if (flagByte == TAP_FLAG_HEADER) {
            return buffer.size() >= TAP_HEADER_LENGTH;
        }
        // For data blocks, we rely on external signals (pilot tone, etc.)
        return false;
    }

    /**
     * Builds the final TapBlock from accumulated data.
     *
     * @return Completed TapBlock
     * @throws IllegalStateException if no data accumulated
     */
    public TapBlock build() {
        if (buffer.size() == 0) {
            throw new IllegalStateException("Cannot build empty block");
        }
        return new TapBlock(buffer.toByteArray());
    }

    /**
     * Resets the builder for a new block.
     */
    public void reset() {
        buffer.reset();
        hasFlag = false;
        flagByte = -1;
    }

    /**
     * Gets the currently accumulated data.
     *
     * @return Copy of accumulated bytes
     */
    public byte[] getData() {
        return buffer.toByteArray();
    }

    /**
     * Creates a standard BASIC program header.
     *
     * @param filename   Program name (max 10 chars)
     * @param dataLength Length of the following data block
     * @param autostart  Autostart line number (or >= 32768 for none)
     * @param varOffset  Variables offset
     * @return Header TapBlock
     */
    public static TapBlock createProgramHeader(String filename, int dataLength,
                                               int autostart, int varOffset) {
        byte[] payload = new byte[17];
        payload[0] = HEADER_TYPE_PROGRAM;

        // Filename padded to 10 chars with spaces
        byte[] nameBytes = filename.getBytes();
        Arrays.fill(payload, 1, 11, (byte) ' ');
        System.arraycopy(nameBytes, 0, payload, 1, Math.min(nameBytes.length, 10));

        // Data length (little-endian)
        payload[11] = (byte) (dataLength & 0xFF);
        payload[12] = (byte) ((dataLength >> 8) & 0xFF);

        // Autostart line (little-endian)
        payload[13] = (byte) (autostart & 0xFF);
        payload[14] = (byte) ((autostart >> 8) & 0xFF);

        // Variables offset (little-endian)
        payload[15] = (byte) (varOffset & 0xFF);
        payload[16] = (byte) ((varOffset >> 8) & 0xFF);

        return new TapBlock(TAP_FLAG_HEADER, payload);
    }

    /**
     * Creates a standard CODE header.
     *
     * @param filename     Code name (max 10 chars)
     * @param dataLength   Length of the following data block
     * @param startAddress Load address
     * @return Header TapBlock
     */
    public static TapBlock createCodeHeader(String filename, int dataLength, int startAddress) {
        byte[] payload = new byte[17];
        payload[0] = HEADER_TYPE_CODE;

        // Filename padded to 10 chars with spaces
        byte[] nameBytes = filename.getBytes();
        Arrays.fill(payload, 1, 11, (byte) ' ');
        System.arraycopy(nameBytes, 0, payload, 1, Math.min(nameBytes.length, 10));

        // Data length (little-endian)
        payload[11] = (byte) (dataLength & 0xFF);
        payload[12] = (byte) ((dataLength >> 8) & 0xFF);

        // Start address (little-endian)
        payload[13] = (byte) (startAddress & 0xFF);
        payload[14] = (byte) ((startAddress >> 8) & 0xFF);

        // Unused (typically 32768)
        payload[15] = (byte) 0x00;
        payload[16] = (byte) 0x80;

        return new TapBlock(TAP_FLAG_HEADER, payload);
    }

    /**
     * Creates a data block from raw bytes.
     *
     * @param data Raw data bytes
     * @return Data TapBlock
     */
    public static TapBlock createDataBlock(byte[] data) {
        return new TapBlock(TAP_FLAG_DATA, data);
    }
}
