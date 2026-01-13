package spectrum.jfx.hardware.disk.wd1793;

import lombok.Data;

@Data
public class VirtualDriveImpl implements VirtualDrive {

    byte[] data;
    boolean hasDisk;
    int physicalTrack;

    private boolean dirty;
    String trdFileName;

    @Override
    public void insertNewDisk() {
        hasDisk = true;
        physicalTrack = 0;
        data = new byte[160 * 256 * 2];
        dirty = false;
        trdFileName = null;
    }

}
