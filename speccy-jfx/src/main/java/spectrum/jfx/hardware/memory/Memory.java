package spectrum.jfx.hardware.memory;

import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.ula.OutPortListener;

public interface Memory extends Device, OutPortListener {

    int PAGE_SIZE = 16384;

    // Memory sizes
    int ROM_SIZE = 0x4000;     // 16K ROM
    int RAM_SIZE = 0x10000;     // 64K RAM (16K rom + 16K screen + 32K user)
    int SCREEN_RAM_SIZE = 0x1B00;

    // Map
    int ROM_START = 0x0000;
    int ROM_END = 0x3FFF;
    int SCREEN_RAM_START = 0x4000;
    int SCREEN_RAM_END = 0x7FFF;
    int USER_RAM_START = 0x8000;
    int USER_RAM_END = 0xFFFF;

    int readWord(int address);

    void writeWord(int address, int value);

    int readByte(int address);

    void writeByte(int address, byte value);

    void writeByte(int address, int value);

    byte[] getScreen();

    byte[] getBlock(int startAddress, int length);

    void loadROM(byte[] rom);

    default void loadROM(int bank, byte[] rom) {
        throw new UnsupportedOperationException();
    }

    // Map specific ROMs (like TR-DOS)
    default void mapBank(int bank, byte[] data) {
        throw new UnsupportedOperationException();
    }

    // Unmap specific ROMs and restore previous mapping
    default void unmapBank(int bank) {
        throw new UnsupportedOperationException();
    }

    void loadRoms();

    void flash(int address, byte[] data);

    void reset();

    default boolean isScreenAddress(int address) {
        return address >= 0x4000 && address <= 0x7FFF;
    }

    default void outPort(int port, int value) {
        //do nothing
    }

}
