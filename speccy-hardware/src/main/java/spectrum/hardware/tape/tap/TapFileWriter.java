package spectrum.hardware.tape.tap;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes TAP blocks to a file.
 * <p>
 * TAP file format:
 * - Series of blocks, each prefixed with 2-byte length
 * - No file header or footer
 */
@Slf4j
public class TapFileWriter {

    private final List<TapBlock> blocks = new ArrayList<>();

    /**
     * Adds a block to be written.
     *
     * @param block Block to add
     */
    public void addBlock(TapBlock block) {
        blocks.add(block);
        log.debug("Added block: {}", block);
    }

    /**
     * Gets the number of blocks queued.
     *
     * @return Block count
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Gets all queued blocks.
     *
     * @return List of blocks (copy)
     */
    public List<TapBlock> getBlocks() {
        return new ArrayList<>(blocks);
    }

    /**
     * Clears all queued blocks.
     */
    public void clear() {
        blocks.clear();
    }

    /**
     * Writes all blocks to a file.
     *
     * @param path Path to write to
     * @throws IOException If write fails
     */
    public void writeToFile(Path path) throws IOException {
        try (OutputStream os = Files.newOutputStream(path);
             BufferedOutputStream bos = new BufferedOutputStream(os)) {
            for (TapBlock block : blocks) {
                byte[] tapBytes = block.toTapBytes();
                bos.write(tapBytes);
            }
        }
        log.info("Wrote {} blocks to {}", blocks.size(), path);
    }

    /**
     * Writes all blocks to a file.
     *
     * @param file File to write to
     * @throws IOException If write fails
     */
    public void writeToFile(File file) throws IOException {
        writeToFile(file.toPath());
    }

    /**
     * Writes all blocks to a file.
     *
     * @param filename Path string
     * @throws IOException If write fails
     */
    public void writeToFile(String filename) throws IOException {
        writeToFile(Path.of(filename));
    }

    /**
     * Gets the total size of the TAP file in bytes.
     *
     * @return Total size including length prefixes
     */
    public int getTotalSize() {
        int size = 0;
        for (TapBlock block : blocks) {
            size += block.toTapBytes().length;
        }
        return size;
    }

    /**
     * Converts all blocks to a single byte array.
     *
     * @return TAP file contents
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(getTotalSize());
        for (TapBlock block : blocks) {
            try {
                baos.write(block.toTapBytes());
            } catch (IOException e) {
                // ByteArrayOutputStream doesn't throw IOException
                throw new RuntimeException(e);
            }
        }
        return baos.toByteArray();
    }

    /**
     * Creates a TapFileWriter and loads existing blocks from a file.
     *
     * @param path Path to read from
     * @return Writer with loaded blocks
     * @throws IOException If read fails
     */
    public static TapFileWriter loadFromFile(Path path) throws IOException {
        TapFileWriter writer = new TapFileWriter();
        byte[] fileData = Files.readAllBytes(path);

        int pos = 0;
        while (pos + 2 <= fileData.length) {
            int blockLength = (fileData[pos] & 0xFF) | ((fileData[pos + 1] & 0xFF) << 8);
            pos += 2;

            if (pos + blockLength > fileData.length) {
                log.warn("Truncated block at position {}, expected {} bytes", pos, blockLength);
                break;
            }

            byte[] blockData = new byte[blockLength];
            System.arraycopy(fileData, pos, blockData, 0, blockLength);
            writer.addBlock(new TapBlock(blockData));
            pos += blockLength;
        }

        log.info("Loaded {} blocks from {}", writer.getBlockCount(), path);
        return writer;
    }
}
