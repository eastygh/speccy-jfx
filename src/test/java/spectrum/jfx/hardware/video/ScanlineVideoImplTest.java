package spectrum.jfx.hardware.video;

import org.junit.jupiter.api.Test;
import spectrum.jfx.hardware.memory.Memory;

class ScanlineVideoImplTest implements Memory {

    private final ScanlineVideoImpl video = new ScanlineVideoImpl(this);

    @Test
    void ticksTest() {

    }

    @Override
    public int readWord(int address) {
        return 0;
    }

    @Override
    public void writeWord(int address, int value) {

    }

    @Override
    public int readByte(int address) {
        return 0;
    }

    @Override
    public void writeByte(int address, byte value) {

    }

    @Override
    public void writeByte(int address, int value) {

    }

    @Override
    public void loadROM(byte[] rom) {

    }

    @Override
    public void reset() {

    }

}