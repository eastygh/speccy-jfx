package spectrum.hardware.machine;

import spectrum.hardware.cpu.CPU;
import spectrum.hardware.debug.DebugListener;
import spectrum.hardware.debug.DebugManager;
import spectrum.hardware.input.Kempston;
import spectrum.hardware.memory.Memory;
import spectrum.hardware.sound.Sound;
import spectrum.hardware.tape.CassetteDeck;


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
