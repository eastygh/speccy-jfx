package spectrum.hardware.memory;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.machine.MachineSettings;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static spectrum.hardware.util.EmulatorUtils.loadFile;

/**
 * Memory map Spectrum 48K
 * 0x0000-0x3FFF: ROM (16K)
 * 0x4000-0x7FFF: Screen RAM (16K)
 * 0x8000-0xFFFF: User RAM (32K)
 */
@Slf4j
public class Memory64KImpl implements Memory {

    // RAM/ROM array
    private final byte[] ram;       // RAM (48K)

    private final boolean volatileRam = false;
    private final MachineSettings machineSettings;

    // Volatile if sets
    private static final VarHandle BYTE_ARRAY_HANDLE;

    static {
        try {
            BYTE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Write a protection flag
    @Getter
    private boolean romWriteProtected = true;

    public Memory64KImpl(MachineSettings machineSettings) {
        log.info("Initializing ZX Spectrum memory");

        ram = new byte[RAM_SIZE];
        this.machineSettings = machineSettings;

        clearMemory();

        log.info("Memory initialized: ROM={}K, RAM={}K", ROM_SIZE / 1024, USER_RAM_END - USER_RAM_START / 1024);
    }

    /**
     * Memory clear
     */
    public void clearMemory() {
        log.debug("Clearing memory");

        // Clear rom with romWriteProtected flag
        if (!romWriteProtected) {
            for (int i = 0; i < ROM_SIZE; i++) {
                ram[i] = 0;
            }
        }

        // Clear RAM
        for (int i = SCREEN_RAM_START; i < RAM_SIZE; i++) {
            ram[i] = 0;
        }

        log.debug("Memory cleared");
    }

    /**
     * Load ROM from byte array
     */
    public void loadROM(byte[] romData) {
        if (romData == null) {
            throw new IllegalArgumentException("ROM data cannot be null");
        }

        if (romData.length != ROM_SIZE) {
            log.warn("ROM data size mismatch. Expected: {}, Actual: {}", ROM_SIZE, romData.length);
        }

        log.info("Loading ROM data ({} bytes)", romData.length);

        boolean wasProtected = romWriteProtected;
        romWriteProtected = false;

        if (volatileRam) {
            copyArrayVolatile(romData, 0, ram, 0, Math.min(romData.length, ROM_SIZE));
        } else {
            System.arraycopy(romData, 0, ram, 0, Math.min(romData.length, ROM_SIZE));
        }

        // Restore write protection flag
        romWriteProtected = wasProtected;

        log.info("ROM loaded successfully");
    }

    @Override
    @SneakyThrows
    public void loadRoms() {
        loadROM(loadFile(machineSettings.getRomFilePath01()));
    }

    @Override
    public void reset() {
        clearMemory();
    }

    /**
     * Read byte from memory
     */
    @Override
    public int readByte(int address) {
        address &= 0xFFFF; // 16 bits mask
        return (volatileRam ? readByteVolatile(ram, address) : ram[address]) & 0xFF;
    }

    @Override
    public void writeByte(int address, byte value) {
        writeByte(address, value & 0xFF);
    }

    /**
     * Write byte to memory
     */
    @Override
    public void writeByte(int address, int value) {
        address &= 0xFFFF; // 16 bits mask
        value &= 0xFF;     // 8 bits mask
        if (address == 23743) {
            log.warn("Writing 0x{} to 0x{}", Integer.toHexString(value).toUpperCase(), Integer.toHexString(address).toUpperCase());
        }

        if (address <= ROM_END && romWriteProtected) {
            log.warn("Attempted write to protected ROM at 0x{}", Integer.toHexString(address).toUpperCase());
            return;
        }
        // RAM
        if (volatileRam) {
            writeByteVolatile(ram, address, (byte) value);
        } else {
            ram[address] = (byte) value;
        }
    }

    /**
     * little-endian word read
     */
    @Override
    public int readWord(int address) {
        int low = readByte(address);
        int high = readByte(address + 1);
        return (high << 8) | low;
    }

    /**
     * little-endian word write
     */
    @Override
    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    @Override
    public void flash(int address, byte[] data) {
        writeBlock(address, data);
    }

    @Override
    public void init() {
        clearMemory();
    }

    @Override
    public void open() {
        clearMemory();
    }

    @Override
    public void close() {
        clearMemory();
    }

    @Override
    public byte[] getScreen() {
        return readBlock(SCREEN_RAM_START, SCREEN_RAM_END + 1);
    }

    @Override
    public byte[] getBlock(int startAddress, int length) {
        return readBlock(startAddress, length);
    }

    /**
     * Read block of memory
     */
    public byte[] readBlock(int startAddress, int length) {
        byte[] block = new byte[length];
        for (int i = 0; i < length; i++) {
            block[i] = (byte) readByte(startAddress + i);
        }
        return block;
    }

    /**
     * write block of memory
     */
    public void writeBlock(int startAddress, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            writeByte(startAddress + i, data[i] & 0xFF);
        }
    }

    public void setRomWriteProtected(boolean writeProtected) {
        this.romWriteProtected = writeProtected;
        log.debug("ROM write protection: {}", writeProtected ? "enabled" : "disabled");
    }

    private void writeByteVolatile(byte[] array, int index, byte value) {
        BYTE_ARRAY_HANDLE.setVolatile(array, index, value);
    }

    private byte readByteVolatile(byte[] array, int index) {
        return (byte) BYTE_ARRAY_HANDLE.getVolatile(array, index);
    }

    private void copyArrayVolatile(byte[] src, int srcPos, byte[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        VarHandle.fullFence();
    }

}