package spectrum.jfx.hardware.input;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KempstonImpl implements Kempston {

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

    @Override
    public int inPort(int port) {
        gamePad.poll();
        return translateState();
    }

    /*
    bit 0 (1)  RIGHT
    bit 1 (2)  LEFT
    bit 2 (4)  DOWN
    bit 3 (8)  UP
    bit 4 (16) FIRE
    bit 5–7    not used =0
     */
    private int translateState() {
        int result = 0;
        if (gamePad.right()) result |= 1;
        if (gamePad.left()) result |= 2;
        if (gamePad.down()) result |= 4;
        if (gamePad.up()) result |= 8;
        if (gamePad.fire()) result |= 16;
        return result;
    }

}
