package spectrum.jfx.hardware.cpu;

import z80core.MemIoOps;
import z80core.NotifyOps;
import z80core.Z80;

public class Z80WrapperImpl extends Z80 implements CPU {

    private final MemIoOps delegateMemory;
    private final NotifyOps delegateNotify;

    public Z80WrapperImpl(MemIoOps memory, NotifyOps notify) {
        super(memory, notify);
        this.delegateMemory = memory;
        this.delegateNotify = notify;
    }

    @Override
    public int executeInstruction(int tStatesLimit) {
        long startCycles = delegateMemory.gettStates();
        execute(tStatesLimit);
        return (int) (delegateMemory.gettStates() - startCycles);
    }

    @Override
    public int executeInstruction() {
        long startCycles = delegateMemory.gettStates();
        execute();
        return (int) (delegateMemory.gettStates() - startCycles);
    }

    public void execute() {

        if (prefixOpcode == 0) {
            int opCode = delegateMemory.fetchOpcode(regPC);
            regR++;

            if (breakpointAt.get(regPC)) {
                opCode = delegateNotify.breakpoint(regPC, opCode);
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

}
