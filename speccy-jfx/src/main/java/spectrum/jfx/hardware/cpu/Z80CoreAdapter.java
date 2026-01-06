package spectrum.jfx.hardware.cpu;

import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.snapshot.CPUSnapShot;
import spectrum.jfx.snapshot.mapper.CPUSnapShotMapper;
import z80core.MemIoOps;
import z80core.NotifyOps;
import z80core.Z80;

@Slf4j
public class Z80CoreAdapter extends Z80 implements CPU {

    public Z80CoreAdapter(MemIoOps memory, NotifyOps notify) {
        super(memory, notify);
    }

    @Override
    public int executeInstruction(int tStatesLimit) {
        long startCycles = MemIoImpl.gettStates();
        execute(tStatesLimit);
        return (int) (MemIoImpl.gettStates() - startCycles);
    }

    @Override
    public int executeInstruction() {
        long startCycles = MemIoImpl.gettStates();
        execute();
        return (int) (MemIoImpl.gettStates() - startCycles);
    }

    protected void execute() {

        if (prefixOpcode == 0) {
            int opCode = MemIoImpl.fetchOpcode(regPC);
            regR++;

            if (breakpointAt.get(regPC)) {
                opCode = NotifyImpl.breakpoint(regPC, opCode);
            }

            if (!halted) {
                regPC = (regPC + 1) & 0xffff;
                flagQ = pendingEI = false;
                decodeOpcode(opCode);
            }
        } else {
            int opCode = prefixOpcode;
            prefixOpcode = 0;
            decodeOpcode(opCode);
        }

        lastFlagQ = flagQ;

        if (execDone) {
            NotifyImpl.execDone();
        }

        if (activeNMI) {
            activeNMI = false;
            nmi();
            return;
        }

        if (ffIFF1 && !pendingEI && MemIoImpl.isActiveINT()) {
            interruption();
        }

    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void open() {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public CPUSnapShot getSnapShot() {
        return CPUSnapShotMapper.toCPUSnapShot(getZ80State());
    }

}
