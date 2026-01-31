package spectrum.hardware.ula;

public interface InPortListener {

    /**
     * Get value from port
     *
     * @param port port number
     */
    int inPort(int port);

    /**
     * Exclusive value from port by internal state (not logical or with other listeners)
     *
     * @param port port number
     */
    default boolean isExclusiveValue(int port) {
        return false;
    }

    /**
     * Ignore value from port by internal state
     *
     * @param port port number
     */
    default boolean isIgnoreValue(int port) {
        return false;
    }

}
