package spectrum.jfx.hardware.disk.wd1793;

public interface VirtualDrive {

    void setDirty(boolean dirty);

    void insertBlankDisk();

    void ejectDisk();

    void loadDisk(String fileName);

    void setReadOnly(boolean readOnly);

}
