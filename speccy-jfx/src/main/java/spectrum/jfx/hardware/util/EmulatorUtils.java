package spectrum.jfx.hardware.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility methods for the ZX Spectrum emulator
 */
public class EmulatorUtils {
    private static final Logger logger = LoggerFactory.getLogger(EmulatorUtils.class);

    /**
     * Converts a byte to an unsigned integer
     */
    public static int toUnsigned(byte value) {
        return value & 0xFF;
    }

    /**
     * Converts two bytes to a 16-bit word (little-endian)
     */
    public static int bytesToWord(int low, int high) {
        return (high << 8) | low;
    }

    /**
     * Extracts the low byte from a 16-bit word
     */
    public static int lowByte(int word) {
        return word & 0xFF;
    }

    /**
     * Extracts the high byte from a 16-bit word
     */
    public static int highByte(int word) {
        return (word >> 8) & 0xFF;
    }

    /**
     * Checks the parity of a number (for the P/V flag)
     */
    public static boolean isEven(int value) {
        return Integer.bitCount(value & 0xFF) % 2 == 0;
    }

    /**
     * Formats a byte as a hexadecimal string
     */
    public static String formatByte(int value) {
        return String.format("0x%02X", value & 0xFF);
    }

    /**
     * Formats a word as a hexadecimal string
     */
    public static String formatWord(int value) {
        return String.format("0x%04X", value & 0xFFFF);
    }

    /**
     * Loads a file into a byte array
     */
    public static byte[] loadFile(String filename) throws IOException {
        Path path = Paths.get(filename);
        byte[] data;
        if (Files.exists(path)) {
            data = Files.readAllBytes(path);
        } else {
            // Attempt to load from resources
            data = loadBinaryFileFromResources(filename);
        }
        logger.info("Loaded file: {} ({} bytes)", filename, data.length);
        return data;
    }

    public static byte[] loadBinaryFileFromResources(String fileName) throws IOException {
        URL uri = EmulatorUtils.class.getResource(fileName);
        if (uri == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        try (InputStream inputStream = uri.openStream()) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + fileName);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            return buffer.toByteArray();
        }
    }

    /**
     * Saves a byte array to a file
     */
    public static void saveFile(String filename, byte[] data) throws IOException {
        Path path = Paths.get(filename);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, data);
        logger.info("Saved file: {} ({} bytes)", filename, data.length);
    }

    /**
     * Loads a ZX Spectrum ROM file
     */
    public static byte[] loadROM(String filename) throws IOException {
        byte[] rom = loadFile(filename);

        if (rom.length != 16384) {
            throw new IOException(String.format("Invalid ROM size: expected 16384 bytes, got %d", rom.length));
        }

        logger.info("ROM loaded successfully: {}", filename);
        return rom;
    }

    /**
     * Formats memory size into a human-readable format
     */
    public static String formatMemorySize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Checks if a file is readable
     */
    public static boolean isFileReadable(String filename) {
        try {
            File file = new File(filename);
            return file.exists() && file.canRead() && file.isFile();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a string is a valid filename
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        // Forbidden characters in filenames
        String forbidden = "<>:\"/\\|?*";
        for (char c : forbidden.toCharArray()) {
            if (filename.indexOf(c) >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the file extension
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Private constructor for a utility class
     */
    private EmulatorUtils() {
        // Utility class should not be instantiated
    }
}