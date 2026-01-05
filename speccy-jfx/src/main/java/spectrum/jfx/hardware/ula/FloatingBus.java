package spectrum.jfx.hardware.ula;

public class FloatingBus implements InPortListener {

    @Override
    public int inPort(int port) {
        return 0xFF;
    }

}
