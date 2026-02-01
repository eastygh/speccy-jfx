package spectrum.hardware.memory;

import machine.MachineTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spectrum.hardware.cpu.CPU;
import spectrum.hardware.cpu.Z80CoreAdapter;
import spectrum.hardware.machine.MachineSettings;
import spectrum.hardware.ula.Ula;
import spectrum.hardware.ula.UlaImpl;
import z80core.NotifyOps;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PokeMemoryTest implements NotifyOps {

    spectrum.hardware.memory.Memory memory;
    Ula ula;
    CPU cpu;

    @BeforeEach
    void setUp() {
        MachineSettings settings = MachineSettings.builder()
                .machineType(MachineTypes.SPECTRUM48K)
                .build();
        memory = new Memory64KImpl(settings);
        ula = new UlaImpl(memory, settings);
        cpu = new Z80CoreAdapter(ula, this);
        ((Memory64KImpl) memory).setRomWriteProtected(true);
    }

    @Test
    void testPokeHighAddress() {
        // LD HL, 0xAFC8 (45000)
        // LD (HL), 0
        int address = 0xAFC8;

        // Машинный код: 
        // 21 C8 AF (LD HL, 0xAFC8)
        // 36 00    (LD (HL), 0)

        ((Memory64KImpl) memory).setRomWriteProtected(false);
        memory.writeByte(0x0000, 0x21);
        memory.writeByte(0x0001, 0xC8);
        memory.writeByte(0x0002, 0xAF);
        memory.writeByte(0x0003, 0x36);
        memory.writeByte(0x0004, 0x00);
        ((Memory64KImpl) memory).setRomWriteProtected(true);

        cpu.setRegPC(0);
        cpu.executeInstruction(); // LD HL, 0xAFC8
        cpu.executeInstruction(); // LD (HL), 0

        assertEquals(0, memory.readByte(address));
    }

    @Override
    public int breakpoint(int address, int opcode) {
        return opcode;
    }

    @Override
    public void execDone() {
    }
}
