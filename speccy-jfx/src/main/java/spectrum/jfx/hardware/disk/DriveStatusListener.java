package spectrum.jfx.hardware.disk;

@FunctionalInterface
public interface DriveStatusListener {

    void onActiveStateChanged(int driveIdx, boolean isActive);

}
