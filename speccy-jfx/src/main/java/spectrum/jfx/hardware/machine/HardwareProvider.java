package spectrum.jfx.hardware.machine;

import spectrum.jfx.debug.DebugListener;
import spectrum.jfx.debug.DebugManager;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.input.Kempston;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.sound.Sound;
import spectrum.jfx.hardware.tape.CassetteDeck;

public interface HardwareProvider {

    CassetteDeck getCassetteDeck();

    Memory getMemory();

    Emulator getEmulator();

    Kempston getKempston();

    Sound getSound();

    CPU getCPU();

    DebugManager getDebugManager();

    void setDebugListener(DebugListener listener);

}
