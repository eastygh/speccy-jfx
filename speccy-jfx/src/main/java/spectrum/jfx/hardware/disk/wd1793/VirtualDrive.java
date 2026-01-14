package spectrum.jfx.hardware.disk.wd1793;

public interface VirtualDrive {

    void setDirty(boolean dirty);

    void insertBlankDisk();

    void ejectDisk();

    void loadDisk(String fileName);

    void loadRawData(byte[] data);

    void setReadOnly(boolean readOnly);

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
     * @param delta - delta to change the physical track
     */
    void deltaPhysicalTrack(int delta);

    int getPhysicalTrack();

    boolean isHasDisk();

    int dataSize();

}
