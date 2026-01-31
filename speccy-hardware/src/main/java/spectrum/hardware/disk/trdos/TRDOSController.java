package spectrum.hardware.disk.trdos;

public interface TRDOSController {

    /**
     * TR-DOS functionality
     * Enable TR-DOS rom
     */
    void switchToTRDOS();

    /**
     * TR-DOS functionality
     * Disable TR-DOS rom
     */
    void switchToZXSpectrum();

}
