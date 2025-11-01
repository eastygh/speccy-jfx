package spectrum.jfx.z80.memory;

public interface Memory {

    int readWord(int address);

    void writeWord(int address, int value);

    int readByte(int address);

    void writeByte(int address, byte value);

    void writeByte(int address, int value);

    void loadROM(byte[] rom);

    void reset();

}
