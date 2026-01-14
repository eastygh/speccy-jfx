package spectrum.jfx.hardware.disk.wd1793;

import lombok.Data;
import spectrum.jfx.hardware.disk.VirtualDrive;

@Data
public class VirtualDriveImpl implements VirtualDrive {

    byte[] data;
    boolean hasDisk;
    int physicalTrack;

    private boolean readOnly;
    private boolean dirty;
    String trdFileName;

    @Override
    public void insertBlankDisk() {
        hasDisk = true;
        physicalTrack = 0;
        data = new byte[160 * 256 * 2];
        dirty = false;
        trdFileName = null;
    }

    @Override
    public void ejectDisk() {
        data = null;
        hasDisk = false;
        trdFileName = "";
    }

    @Override
    public void loadDisk(String fileName) {

    }

    @Override
    public void loadRawData(byte[] data) {
        this.data = data;
        hasDisk = true;
        physicalTrack = 0;
        dirty = false;
        trdFileName = null;
    }

    @Override
    public byte readByte(int offset) {
        return data[offset];
    }

    @Override
    public boolean writeByte(int offset, byte value) {
        if (readOnly) {
            return false;
        }
        data[offset] = value;
        return true;
    }

    @Override
    public int dataSize() {
        return data != null ? data.length : 0;
    }

    @Override
    public void deltaPhysicalTrack(int delta) {
        this.physicalTrack += delta;
    }

}
