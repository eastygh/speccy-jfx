package spectrum.hardware.machine;

public interface Driver {

    void init();

    /**
     * Initialize the driver with the given settings.
     *
     * @param settings - the settings to use
     */
    default void init(MachineSettings settings) {
        init();
    }

    void reset();

    void close();

}
