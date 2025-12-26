package spectrum.jfx.hardware.machine;

import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.input.Kempston;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.tape.CassetteDeck;

public interface HardwareProvider {

    CassetteDeck getCassetteDeck();

    Memory getMemory();

    Emulator getEmulator();

    Kempston getKempston();

    CPU getCPU();

}
