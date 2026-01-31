package spectrum.hardware.disk.wd1793;

/**
 * Represents the operational state of the WD1793 disk controller.
 */
public enum ControllerState {

    /**
     * Controller is idle and ready to accept new commands.
     */
    IDLE,

    /**
     * Controller is searching for sector/track or performing mechanical operations.
     */
    SEARCHING,

    /**
     * Controller is actively transferring data between host and disk.
     */
    TRANSFERRING
}
