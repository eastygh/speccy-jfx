package spectrum.jfx.hardware.memory;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.machine.MachineSettings;

import java.util.Arrays;

import static spectrum.jfx.hardware.util.EmulatorUtils.loadFile;

@Slf4j
public class Memory128KImpl implements Memory {

    private final MachineSettings machineSettings;

    private final byte[][] ramBanks = new byte[8][PAGE_SIZE];
    private final byte[][] romBanks = new byte[2][PAGE_SIZE];
    private final byte[][] currentMapping = new byte[4][];

    private volatile boolean pagingLocked = false;
    private volatile int lastConfiguration = 0;
    private volatile int activeVideoBank = 5; // default 5th bank

    // Write a protection flag
    @Getter
    private boolean romWriteProtected = true;

    public Memory128KImpl(MachineSettings machineSetting) {
        this.machineSettings = machineSetting;
    }

    @Override
    public int readWord(int address) {
        int lsb = readByte(address);
        int msb = readByte(address + 1);
        return (msb << 8) | lsb;
    }

    @Override
    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    @Override
    public int readByte(int address) {
        int window = (address >> 14) & 3; // 0, 1, 2 or 3
        int offset = address & 0x3FFF;   // offset 16КБ
        byte[] bank = currentMapping[window];
        if (bank == null) {
            log.warn("Attempt to read from unmapped memory at address: {}", address);
            return 0;
        }
        return bank[offset] & 0xFF;
    }

    @Override
    public void writeByte(int address, byte value) {
        writeByte(address, value & 0xFF);
    }

    @Override
    public void writeByte(int address, int value) {
        int window = (address >> 14) & 3; // 0, 1, 2 or 3
        if (window == 0 && romWriteProtected) {
            log.trace("ROM write protection is enabled. Write operation is ignored at address: {}", address);
            return;
        }
        int offset = address & 0x3FFF;// offset 16КБ
        currentMapping[window][offset] = (byte) (value & 0xFF);
    }

    @Override
    public byte[] getScreen() {
        byte[] currentScreen = currentMapping[1];
        byte[] copy = new byte[SCREEN_RAM_SIZE];
        System.arraycopy(currentScreen, 0, copy, 0, SCREEN_RAM_SIZE);
        return copy;
    }

    @Override
    public byte[] getBlock(int startAddress, int length) {
        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            int currentAddr = startAddress + i;
            if (currentAddr > 0xFFFF) {
                break;
            }
            int window = (currentAddr >> 14) & 3;
            int offset = currentAddr & 0x3FFF;

            result[i] = currentMapping[window][offset];
        }

        return result;
    }

    @Override
    public void loadROM(byte[] rom) {
        loadROM(0, rom);
    }

    @Override
    public void loadROM(int bank, byte[] romData) {
        bank = bank & 1;
        System.arraycopy(romData, 0, romBanks[bank], 0, Math.min(romData.length, ROM_SIZE));
    }

    @Override
    @SneakyThrows
    public void loadRoms() {
        loadROM(0, loadFile(machineSettings.getRomFilePath01()));
        loadROM(1, loadFile(machineSettings.getRomFilePath02()));
    }

    @Override
    public void flash(int address, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            writeByte(address + i, data[i]);
        }
    }

    @Override
    public void reset() {
        pagingLocked = false;
        lastConfiguration = 0;
        configureMapping(lastConfiguration);
        currentMapping[1] = ramBanks[5]; // $4000-$7FFF Bank 5
        currentMapping[2] = ramBanks[2]; // $8000-$BFFF Bank 2
        clearMemory();
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void open() {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public void outPort(int port, int value) {
        // check is our port
        if ((port & 0x8002) == 0) {
            configureMapping(value);
        }
    }

    private void configureMapping(int value) {
        if (pagingLocked) {
            return;
        }
        lastConfiguration = value;
        // Bit 4: ROM selection
        currentMapping[0] = romBanks[(value >> 4) & 1];

        // Bits 0-2: RAM selection for an upper window
        currentMapping[3] = ramBanks[value & 7];

        // bit 3: video bank selection for ula
        activeVideoBank = ((value >> 3) & 1) == 1 ? 7 : 5;

        // bit 5: port blocking
        if (((value >> 5) & 1) == 1) {
            pagingLocked = true;
        }
    }

    private void clearMemory() {
        for (byte[] ramBank : ramBanks) {
            Arrays.fill(ramBank, (byte) 0);
        }
    }

    private void clearRom() {
        for (byte[] romBank : romBanks) {
            Arrays.fill(romBank, (byte) 0);
        }
    }
}
