package spectrum.hardware.disk;

import spectrum.hardware.disk.wd1793.sound.FloppySoundEngine;
import spectrum.hardware.machine.Device;
import spectrum.hardware.ula.ClockListener;
import spectrum.hardware.ula.InPortListener;
import spectrum.hardware.ula.OutPortListener;
import spectrum.hardware.ula.Ula;

/**
 * General interface for Floppy Disk Controllers (e.g., WD1793, MB02, +3 Disk).
 */
public interface DiskController extends Device, ClockListener, InPortListener, OutPortListener {

    /**
     * Gets a specific drive by index.
     *
     * @param driveIdx - index of the drive (0-based A,B,C,D)
     * @return the VirtualDrive instance for the specified drive or null if not available
     */
    VirtualDrive getDrive(int driveIdx);

    /**
     * Sets the listener for drive activity events (LEDs for UI)
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

    /**
     * Initializes the controller with the ULA
     */
    default boolean initWithULA(Ula ula) {
        return false;
    }

    /**
     * Retrieves the number of disk drives supported by the controller
     */
    int getDiskCount();

    /**
     * Sets the sound output for the sound emulation of FDD/HDD
     *
     * @param soundFloppyEngine Sound Engine instance
     */
    default void setFloppySoundEngine(FloppySoundEngine soundFloppyEngine) {
        // no-op
    }

}