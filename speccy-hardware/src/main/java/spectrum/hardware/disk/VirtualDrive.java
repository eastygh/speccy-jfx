package spectrum.hardware.disk;

import java.io.File;

public interface VirtualDrive {

    void setDirty(boolean dirty);

    void insertBlankDisk();

    void ejectDisk();

    void loadDisk(String fileName);

    void loadDisk(File file);

    void loadRawData(byte[] data);

    void setReadOnly(boolean readOnly);

    /**
     * Checks if the drive is read-only.
     */
    boolean isReadOnly();

    /**
     * Get the file name of the disk loaded from
     * @return the file name of the disk loaded from
     */
    String getFileName();

    byte readByte(int offset);

    /**
     * Write a byte to the drive.
     *
     * @param offset - absolute offset in the drive data
     * @param value  - byte to write
     * @return true if the byte was written, false if the drive is read-only or error
     */
    boolean writeByte(int offset, byte value);

    void setPhysicalTrack(int physicalTrack);

    /**
     * Change the physical track by delta. (+ or -)
     *
     * @param delta - delta to change the physical track
     */
    void deltaPhysicalTrack(int delta);

    int getPhysicalTrack();

    boolean isHasDisk();

    int dataSize();

    /**
     * Flush the drive data to the file if supported.
     */
    void flush();

}
