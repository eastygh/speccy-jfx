package spectrum.jfx.hardware.input;

import lombok.extern.slf4j.Slf4j;
import org.lwjgl.glfw.GLFWGamepadState;

import static org.lwjgl.glfw.GLFW.*;

@Slf4j
public class GamePadGLFWImpl implements GamePad {

    private int gamepadIndex = -1;

    boolean left;
    boolean right;
    boolean up;
    boolean down;
    boolean fire;

    @Override
    public void init() {
        if (!glfwInit()) {
            log.error("GLFW initialization failed");
            return;
        }
        gamepadIndex = getGamepadIndex();
        log.info("Gamepad index: {}", gamepadIndex);
    }

    @Override
    public void shutdown() {
        glfwTerminate();
    }

    @Override
    public void reset() {

    }

    @Override
    public void poll() {

        GLFWGamepadState state = GLFWGamepadState.create();

        boolean ok = glfwGetGamepadState(gamepadIndex, state);
        if (!ok) {
            log.error("Failed to get gamepad state");
            return;
        }

        float x = state.axes(GLFW_GAMEPAD_AXIS_LEFT_X);
        float y = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y);

        left = x < -0.5f;
        right = x > 0.5f;
        up = y < -0.5f;
        down = y > 0.5f;

        fire = state.buttons(GLFW_GAMEPAD_BUTTON_A) == GLFW_PRESS;
    }

    @Override
    public boolean up() {
        return up;
    }

    @Override
    public boolean down() {
        return down;
    }

    @Override
    public boolean left() {
        return left;
    }

    @Override
    public boolean right() {
        return right;
    }

    @Override
    public boolean fire() {
        return fire;
    }

    private int getGamepadIndex() {
        int joystick = -1;

        for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_16; jid++) {
            if (glfwJoystickPresent(jid) && glfwJoystickIsGamepad(jid)) {
                joystick = jid;
                break;
            }
        }
        if (joystick == -1) {
            log.error("No gamepad found");
        }
        return joystick;
    }

}
