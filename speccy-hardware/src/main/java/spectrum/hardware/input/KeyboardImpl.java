package spectrum.hardware.input;

import lombok.Getter;
import lombok.Setter;

public class KeyboardImpl implements Keyboard {

    @Getter
    @Setter
    private KeyboardDriver keyboardDriver;

    @Override
    public void init() {

    }

    @Override
    public void reset() {

    }

    @Override
    public void open() {
        // do nothing
    }

    @Override
    public void close() {

    }

    @Override
    public int inPort(int port) {
        return 0;
    }

}
