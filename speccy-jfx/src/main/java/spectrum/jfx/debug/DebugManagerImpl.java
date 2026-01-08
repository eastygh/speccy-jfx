package spectrum.jfx.debug;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.machine.HardwareProvider;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static spectrum.jfx.hardware.memory.Memory64KImpl.RAM_SIZE;

@Slf4j
public class DebugManagerImpl implements DebugListener, DebugManager {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition canProceed = lock.newCondition();
    private final AtomicReference<DebugListener> debugListenerRef = new AtomicReference<>();

    @Getter
    private volatile boolean paused = false;
    private volatile boolean stepMode = false;

    private volatile boolean hasBreakpoints = false;
    private final BitSet breakpoints = new BitSet(RAM_SIZE);

    @Override
    public void preExecuteCheck(HardwareProvider hardwareProvider) {
        CPU cpu = hardwareProvider.getCPU();
        boolean atBreakpoint = hasBreakpoints && breakpoints.get(cpu.getRegPC() & 0xFFFF);

        if (!paused && !atBreakpoint) {
            return;
        }

        lock.lock();
        try {
            if (atBreakpoint) {
                paused = true;
            }
            getListener().onStepComplete(hardwareProvider, atBreakpoint ? SuspendType.BREAKPOINT : SuspendType.STEP);
            while (paused) {
                canProceed.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void postExecuteCheck(HardwareProvider hardwareProvider) {
        if (stepMode) {
            lock.lock();
            try {
                paused = true;
                stepMode = false;
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void resume() {
        lock.lock();
        try {
            paused = false;
            stepMode = false;
            canProceed.signalAll();
        } finally {
            lock.unlock();
        }
        getListener().onResumed();
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void stepInto() {
        lock.lock();
        try {
            paused = false;
            stepMode = true;
            canProceed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addBreakpoint(int address) {
        lock.lock();
        try {
            breakpoints.set(address & 0xFFFF);
            hasBreakpoints = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isBreakpoint(int address) {
        return breakpoints.get(address & 0xFFFF);
    }

    @Override
    public boolean hasBreakpoints() {
        return hasBreakpoints;
    }

    @Override
    public void removeBreakpoint(int address) {
        lock.lock();
        try {
            breakpoints.clear(address & 0xFFFF);
            hasBreakpoints = !breakpoints.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clearAllBreakpoints() {
        lock.lock();
        try {
            breakpoints.clear();
            hasBreakpoints = false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setDebugListener(DebugListener debugListener) {
        debugListenerRef.set(debugListener);
    }

    @Override
    public void onStepComplete(HardwareProvider hv, SuspendType suspendType) {
        log.warn("Fake hook called - onStepComplete");
    }

    @Override
    public void onResumed() {
        log.warn("Fake hook called - onResumed");
    }

    private DebugListener getListener() {
        DebugListener debugListener = debugListenerRef.get();
        return debugListener != null ? debugListener : this;
    }

}
