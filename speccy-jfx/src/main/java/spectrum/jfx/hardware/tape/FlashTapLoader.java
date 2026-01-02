package spectrum.jfx.hardware.tape;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.cpu.BreakPointListener;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.machine.HardwareProvider;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.machine.Machine;
import spectrum.jfx.model.TapeFile;
import spectrum.jfx.model.TapeSection;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public class FlashTapLoader {

    public static final int LAST_KEY = 0x5C08;
    public static final int FLAGS = 0x5C3B;
    public static final int LOAD_PROC_ADDRESS = 0x0556;

    private final TapeFile tapeFile;
    private final HardwareProvider hardwareProvider;

    private int currentSectionIndex = 0;
    private volatile boolean finished = false;

    public void load() {
        if (tapeFile == null || hardwareProvider == null) {
            log.error("Tape file or emulator not set");
            return;
        }
        if (isEmpty(tapeFile.getSections())) {
            log.error("Tape file has no data");
            return;
        }
        Machine.withHardwareProvider(this::fastLoad);
    }

    @SneakyThrows
    private void fastLoad(HardwareProvider hv) {
        new Thread(() -> loadThread(hv)).start();
    }

    @SneakyThrows
    private void loadThread(HardwareProvider hv) {
        int timeout = 0;
        BreakPointListener currentListener = hv.getEmulator().addBreakPointListener(LOAD_PROC_ADDRESS, this::processLoadCommand);
        try {
            triggerLoadCommand(hardwareProvider.getMemory(), null);
            while (!finished && timeout < 1000) {
                Thread.sleep(10);
                timeout++;
            }
            if (!finished) {
                log.warn("Tape loading timeout");
                finished = true;
            }
        } finally {
            hv.getEmulator().removeBreakPointListener(LOAD_PROC_ADDRESS);
            if (currentListener != null) {
                hv.getEmulator().addBreakPointListener(LOAD_PROC_ADDRESS, currentListener);
            }
        }
    }

    private int processLoadCommand(int address, int opcode) {
        if (address != LOAD_PROC_ADDRESS || finished) {
            return opcode;
        }
        if (currentSectionIndex >= tapeFile.getSections().size()) {
            finished = true;
            return opcode;
        }
        flashTap();
        currentSectionIndex++;
        return 0xC9;
    }

    private void flashTap() {
        TapeSection currentSection = tapeFile.getSections().get(currentSectionIndex);
        log.info("Loading section type={} {}/{}", currentSection.getType(), currentSectionIndex, currentSection.getLength());
        CPU cpu = hardwareProvider.getCPU();
        Memory memory = hardwareProvider.getMemory();

        log.info("Needed data type (regA): {}", cpu.getRegA());

        // Simple TAP file
        int addr = cpu.getRegIX();    // Address start
        int nBytes = cpu.getRegDE();
        int dataType = currentSection.getData()[0] & 0xFF;
        if (cpu.getRegA() != dataType) {
            log.warn("Wrong data type (regA): {} (expected: {})", cpu.getRegA(), dataType);
        }
        byte[] data = new byte[nBytes];
        System.arraycopy(currentSection.getData(), 1, data, 0, nBytes);
        memory.flash(addr, data);

        cpu.setRegA(xorData(dataType, data));
        cpu.setCarryFlag(true);
        cpu.setRegIX(addr + nBytes + 1);
        cpu.setRegDE(0);

//        if (currentSectionIndex > 1) {
//            currentSectionIndex = 10;
//            finished = true;
//        }

    }

    private int xorData(int start, byte[] data) {
        int xor = start;
        for (int i = 0; i < data.length - 1; i++) {
            xor ^= data[i];
            xor &= 0xFF;
        }
        return xor & 0xFF;
    }

    @SneakyThrows
    public static void triggerLoadCommand(Memory memory, CPU cpu) {
        memory.writeByte(LAST_KEY, 0xEF); // LOAD keyword
        memory.writeByte(FLAGS, memory.readByte(FLAGS) | 0x20); // flag that a key was pressed
        Thread.sleep(30);
        memory.writeByte(LAST_KEY, 0x22); // "
        memory.writeByte(FLAGS, memory.readByte(FLAGS) | 0x20);
        Thread.sleep(30);
        memory.writeByte(LAST_KEY, 0x22); // "
        memory.writeByte(FLAGS, memory.readByte(FLAGS) | 0x20);
        Thread.sleep(30);
        memory.writeByte(LAST_KEY, 0x0D); // ENTER
        memory.writeByte(FLAGS, memory.readByte(FLAGS) | 0x20);
        Thread.sleep(30);
    }


}
