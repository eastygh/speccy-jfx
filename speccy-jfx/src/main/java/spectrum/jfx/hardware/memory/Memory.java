package spectrum.jfx.hardware.memory;

import spectrum.jfx.hardware.machine.Device;

public interface Memory extends Device {

    int readWord(int address);

    void writeWord(int address, int value);

    int readByte(int address);

    void writeByte(int address, byte value);

    void writeByte(int address, int value);

    void loadROM(byte[] rom);

    void flash(int address, byte[] data);

    void reset();

    boolean isScreenAddress(int address);

}
