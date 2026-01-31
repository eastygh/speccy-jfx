package spectrum.hardware.ula;

public interface OutPortListener {

    /**
     * Output value to port
     *
     * @param port  port number
     * @param value value to output
     */
    void outPort(int port, int value);

}
