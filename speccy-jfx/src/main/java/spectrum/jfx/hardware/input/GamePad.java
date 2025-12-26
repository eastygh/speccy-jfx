package spectrum.jfx.hardware.input;

public interface GamePad {

    void init();

    void reset();

    void shutdown();

    void poll();

    boolean up();

    boolean down();

    boolean left();

    boolean right();

    boolean fire();

}
