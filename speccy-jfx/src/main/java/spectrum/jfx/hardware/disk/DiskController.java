package spectrum.jfx.hardware.disk;

import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;

/**
 * General interface for Floppy Disk Controllers (e.g., WD1793, MB02, +3 Disk).
 */
public interface DiskController extends Device, ClockListener, InPortListener, OutPortListener {

    /**
     * Loads disk image data (supports TRD, SCL, etc.)
     */
    void loadDisk(int drive, byte[] data);

    /**
     * Sets the listener for drive activity events (LEDs)
     */
    void setDriveStatusListener(DriveStatusListener listener);

    /**
     * Checks if a specific drive's activity LED is on
     */
    boolean isDriveLedOn(int driveIdx);

    /**
     * Triggers the Magic Button (NMI) logic
     */
    void triggerNMI();

    /**
     * Sets the active state of the disk controller
     */
    void setActive(boolean active);

}