package spectrum.hardware.disk.wd1793;

import lombok.Data;
import lombok.SneakyThrows;
import spectrum.hardware.disk.DiskImageAdapter;
import spectrum.hardware.disk.VirtualDrive;
import spectrum.hardware.util.EmulatorUtils;

import java.io.File;

import static spectrum.hardware.disk.wd1793.TrdDiskGeometry.*;
import static spectrum.hardware.util.EmulatorUtils.isFileReadable;

@Data
public class VirtualDriveImpl implements VirtualDrive {

    byte[] data;
    boolean hasDisk;
    int physicalTrack;
    int index;

    private boolean readOnly;
    private boolean dirty;
    private String trdFileName;
    private boolean scl;

    public VirtualDriveImpl(int index) {
        this.index = index;
    }

    @Override
    public String getFileName() {
        return trdFileName;
    }

    @Override
    public void insertBlankDisk() {
        hasDisk = true;
        physicalTrack = 0;
        readOnly = false;
        dirty = false;
        trdFileName = "BLANK-DSK-" + index + ".trd";
        if (isFileReadable(trdFileName)) {
            loadDisk(trdFileName);
        } else {
            data = new byte[BYTES_PER_TRACK * TRACKS * SIDES];
        }
    }

    @Override
    public void ejectDisk() {
        data = null;
        hasDisk = false;
        trdFileName = "";
        physicalTrack = 0;
        dirty = false;
        scl = false;
        readOnly = false;
    }

    @Override
    @SneakyThrows
    public void loadDisk(String fileName) {
        this.trdFileName = fileName;
        byte[] trdData = EmulatorUtils.loadFile(fileName);
        if (DiskImageAdapter.isScl(trdData)) {
            this.scl = true;
            this.data = DiskImageAdapter.convertToTrd(trdData);
        } else {
            this.scl = false;
            this.data = trdData;
        }
        this.hasDisk = true;
        this.physicalTrack = 0;
        this.dirty = false;
    }

    @Override
    @SneakyThrows
    public void loadDisk(File file) {
        loadDisk(file.getAbsolutePath());
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
        dirty = true;
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

    @Override
    @SneakyThrows
    public void flush() {
        if (!readOnly && dirty) {
            if (scl) {
                byte[] sclData = DiskImageAdapter.convertToScl(data);
                EmulatorUtils.saveFile(trdFileName, sclData);
            } else {
                EmulatorUtils.saveFile(trdFileName, data);
            }
            dirty = false;
        }
    }

}
