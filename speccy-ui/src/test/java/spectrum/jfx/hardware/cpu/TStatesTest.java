package spectrum.jfx.hardware.cpu;

import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spectrum.hardware.cpu.Z80CoreAdapter;
import spectrum.hardware.machine.MachineSettings;
import spectrum.hardware.memory.Memory;
import spectrum.hardware.memory.Memory64KImpl;
import spectrum.hardware.ula.OutPortListener;
import spectrum.hardware.ula.Ula;
import spectrum.hardware.ula.UlaImpl;
import z80core.NotifyOps;
import z80core.Z80;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static spectrum.hardware.memory.Memory64KImpl.ROM_SIZE;
import static spectrum.hardware.util.EmulatorUtils.loadFile;

@Disabled
@Slf4j
class TStatesTest implements NotifyOps, OutPortListener {

    Memory memory;
    spectrum.hardware.cpu.CPU cpu;
    Ula ula;

    AtomicBoolean done = new AtomicBoolean(false);
    MachineSettings machineSettings = MachineSettings.builder()
            .machineType(MachineTypes.SPECTRUM48K)
            .audioSampleRate(44100)
            .build();

    @BeforeEach
    void init() {
        memory = new Memory64KImpl(machineSettings);
        ((Memory64KImpl) memory).setRomWriteProtected(false);
        ula = new UlaImpl(memory, machineSettings);
        ula.addPortListener(0xFE, this);
        cpu = new Z80CoreAdapter(ula, this);
    }

    @Test
    void cycleTest() {
        int tStates = 0;

        loadInstruction(0x0000);
        tStates = cpu.executeInstruction();
        assertEquals(7, tStates);
        tStates = cpu.executeInstruction();
        assertEquals(7, tStates);
        tStates = cpu.executeInstruction();
        assertEquals(4, tStates);
        tStates = cpu.executeInstruction();
        assertEquals(4, tStates);
        tStates = cpu.executeInstruction();
        assertEquals(10, tStates);
    }

    @Test
    void zexallTest() throws IOException {

        cpu.reset();

        byte[] zexall = loadFile("/tests/zexall.com");
        byte[] rom = new byte[ROM_SIZE];
        System.arraycopy(zexall, 0, rom, 0x100, zexall.length);
        memory.reset();
        memory.loadROM(rom);
        memory.writeByte(0x0005, (byte) 0xC9);

        Z80CoreAdapter z80core = (Z80CoreAdapter) cpu;

        z80core.setRegPC(0x100);
        z80core.setRegSP(0xF000);

        done.set(false);
        while (!done.get()) {
            cpu.executeInstruction();
            if (z80core.getRegPC() == 5) {
                handleBDOSCall();
                memory.writeByte(0x0005, (byte) 0xC9);
            }
        }

    }

    @Test
    void zexdocTest() throws IOException {
        cpu.reset();
        memory.reset();
        ula.reset();
        byte[] zexdoc = loadFile("/tests/zexdoc.com");
        byte[] rom = new byte[ROM_SIZE];
        System.arraycopy(zexdoc, 0, rom, 0x100, zexdoc.length);
        memory.loadROM(rom);
        cpu.setRegPC(0x100);
        cpu.setRegSP(0xF000);

        done.set(false);
        while (!done.get()) {
            cpu.executeInstruction();
            if (cpu.getRegPC() == 5) {
                handleBDOSCall();
                memory.writeByte(0x0005, (byte) 0xC9);
            }
        }
    }

    private void handleBDOSCall() {
        Z80 z80core = (Z80) cpu;
        int function = z80core.getRegC();
        switch (function) {
            case 0x00: // System reset
                done.set(true);
                break;
            case 0x02: // Console output
                log.info("{}", (char) z80core.getRegE());
                break;
            case 0x09: // Print string
                printString(z80core.getRegDE());
                break;
        }
    }

    private void printString(int address) {
        int value = memory.readByte(address);
        StringBuilder str = new StringBuilder();
        while (value != '$') {
            str.append((char) value);
            address++;
            value = memory.readByte(address);
        }
        log.info("{}", str);
    }

    private void loadInstruction(int address) {
//        LD A, 42h        ; 3E 42       - 7 тактов
//        LD B, 10h        ; 06 10       - 7 тактов
//        LD C, B          ; 48          - 4 такта
//        LD D, A          ; 57          - 4 такта
        // 21 34 12           ; LD HL, 1234h      ; 10     ; 40
        memory.writeByte(address, (byte) 0x3E);
        memory.writeByte(address + 1, (byte) 0x42);
        memory.writeByte(address + 2, (byte) 0x06);
        memory.writeByte(address + 3, (byte) 0x10);
        memory.writeByte(address + 4, (byte) 0x48);
        memory.writeByte(address + 5, (byte) 0x57);
        memory.writeByte(address + 6, (byte) 0x21);
        memory.writeByte(address + 7, (byte) 0x34);
        memory.writeByte(address + 8, (byte) 0x12);
    }


    @Override
    public int breakpoint(int address, int opcode) {
        return 0;
    }

    @Override
    public void execDone() {
        done.set(true);
    }

    @Override
    public void outPort(int port, int value) {
        log.info("outPort: port={}, value={}", port, value);
    }

}
