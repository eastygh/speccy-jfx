package spectrum.jfx.hardware.input;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KempstonImpl implements Kempston {

    @Getter
    GamePad gamePad;

    public KempstonImpl(GamePad gamePad) {
        this.gamePad = gamePad;
    }

    @Override
    public void init() {
        if (gamePad == null) {
            log.warn("No gamepad type configured");
            return;
        }
        gamePad.init();
    }

    /**
     * Listens port 0x1F Kempston Joystick
     *
     * @param port port from event sender
     * @return kempston state
     */
    @Override
    public int inPort(int port) {
        if (port != 0x1F || gamePad == null) {
            return 0;
        }
        gamePad.poll();
        return translateState();
    }

    /*
    bit 0 (1) RIGHT
    bit 1 (2) LEFT
    bit 2 (4) DOWN
    bit 3 (8) UP
    bit 4 (16) FIRE
    bit 5–7 not used =0
     */
    private int translateState() {
        if (gamePad == null) return 0;
        int result = 0;
        if (gamePad.right()) result |= 1;
        if (gamePad.left()) result |= 2;
        if (gamePad.down()) result |= 4;
        if (gamePad.up()) result |= 8;
        if (gamePad.fire()) result |= 16;
        return result;
    }

    @Override
    public void reset() {
        GamePad gp = getGamePad();
        if (gp != null) {
            gp.reset();
        }
    }

    @Override
    public void open() {
        // ignore
    }

    @Override
    public void close() {
        // ignore
    }
}
