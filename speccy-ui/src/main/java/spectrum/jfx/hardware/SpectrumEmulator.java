package spectrum.jfx.hardware;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machine.MachineTypes;
import machine.SpectrumClock;
import spectrum.hardware.cpu.AddressHookListener;
import spectrum.hardware.cpu.CPU;
import spectrum.hardware.debug.DebugListener;
import spectrum.hardware.debug.DebugManager;
import spectrum.hardware.debug.DebugManagerImpl;
import spectrum.hardware.disk.DiskController;
import spectrum.hardware.input.Kempston;
import spectrum.hardware.machine.*;
import spectrum.hardware.memory.Memory;
import spectrum.hardware.sound.Sound;
import spectrum.hardware.tape.CassetteDeckImpl;
import spectrum.hardware.ula.ClockListener;
import spectrum.hardware.ula.InPortListener;
import spectrum.hardware.ula.OutPortListener;
import spectrum.hardware.ula.UlaImpl;
import spectrum.hardware.util.EmulatorUtils;
import spectrum.hardware.video.Video;
import spectrum.jfx.hardware.disk.FloppySoundEngineImpl;
import spectrum.jfx.hardware.input.GamePadGLFWImpl;
import spectrum.hardware.input.KempstonImpl;
import spectrum.jfx.hardware.input.Keyboard;
import spectrum.jfx.hardware.sound.BeeperImpl;
import spectrum.jfx.hardware.sound.ay.AY38912;
import spectrum.jfx.hardware.video.ScanlineVideoImpl;
import z80core.NotifyOps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static spectrum.hardware.factory.CPUFactory.createCPU;
import static spectrum.hardware.factory.DiskFactory.createDiskController;
import static spectrum.hardware.factory.MemoryFactory.createMemory;

@Slf4j
@Getter
public class SpectrumEmulator implements NotifyOps, HardwareProvider, Emulator {

    // ZXCore-lib clock support
    SpectrumClock clock = SpectrumClock.INSTANCE;

    CPU cpu;
    Memory memory;
    Video<?> video;
    Keyboard keyboard;
    Sound sound;
    Sound ay38912;
    spectrum.hardware.ula.Ula ula;
    CassetteDeckImpl cassetteDeck;
    Kempston kempston;
    DiskController diskController;
    DebugManager debugManager = new DebugManagerImpl();

    private final List<Device> devices = new ArrayList<>();

    // Emulation management
    private volatile boolean running;
    private volatile boolean paused;

    private volatile boolean speedUpMode = false;

    // Emulation state
    private volatile boolean hold = false;
    private volatile long frameCounter = 0;

    private static final long FRAME_TIME_NS = 16666666; // ~60 FPS

    private final Map<Integer, AddressHookListener> addressHookListeners = new ConcurrentHashMap<>();
    private final Queue<Runnable> contextsTasks = new ConcurrentLinkedQueue<>();

    private final spectrum.hardware.machine.MachineSettings machineSettings;
    private Thread emulationThread;


    public SpectrumEmulator() {
        machineSettings = spectrum.hardware.machine.MachineSettings
                .ofDefault(CpuImplementation.SANCHES)
                .setMachineType(MachineTypes.SPECTRUM128K);
    }

    public void init() {

        SpectrumClock.INSTANCE.setSpectrumModel(machineSettings.getMachineType());

        this.memory = createMemory(machineSettings);
        this.video = new ScanlineVideoImpl(memory, machineSettings);
        devices.add(video);
        this.keyboard = new Keyboard();
        devices.add(keyboard);
        this.keyboard.resetKeyboard();

        this.ula = new UlaImpl(memory, machineSettings);

        this.ula.addPortListener(0xfd, memory); //  0x7ffd Bank switching
        this.ula.addPortListener(0xfe, keyboard); // keyboard
        this.ula.addPortListener(0xfe, (OutPortListener) video); // Border color

        this.sound = new BeeperImpl(machineSettings); // Sound Beeper
        devices.add(sound);
        this.ula.addPortListener(0xfe, sound); // Sound
        this.ula.addClockListener(sound);

        this.ay38912 = new AY38912(machineSettings); // Sound AY-3-8912
        devices.add(ay38912);
        this.ula.addPortListener(0xfd, ay38912);
        this.ula.addPortListener(0xfd, (InPortListener) ay38912);
        this.ula.addClockListener(ay38912);

        this.cassetteDeck = new CassetteDeckImpl(); // Cassette deck
        devices.add(cassetteDeck);
        this.ula.addPortListener(0xfe, (InPortListener) cassetteDeck); // cassette deck IN
        this.ula.addPortListener(0xfe, (OutPortListener) cassetteDeck); // cassette deck OUT
        if (video instanceof ClockListener videoClock) {
            this.ula.addClockListener(videoClock);
        }
        if (cassetteDeck instanceof ClockListener cassetteDeckClock) {
            this.ula.addClockListener(cassetteDeckClock);
        }
        // For pushback tape sound
        cassetteDeck.setSound(sound);

        this.kempston = new KempstonImpl(new GamePadGLFWImpl());
        devices.add(kempston);
        this.ula.addPortListener(0x1F, kempston);
        this.kempston.init();

        // Disk controller
        diskController = createDiskController(machineSettings, ula);
        if (diskController != null) {
            diskController.setFloppySoundEngine(new FloppySoundEngineImpl(sound));
            devices.add(diskController);
        }

        cpu = createCPU(machineSettings, ula, this);

        Machine.setHardwareProvider(this);

    }

    @Override
    public int breakpoint(int address, int opcode) {
        AddressHookListener listener = addressHookListeners.get(address);
        if (listener != null) {
            return listener.call(address, opcode);
        }
        return opcode;
    }

    @Override
    public void execDone() {
        // ignore
    }

    /**
     * starts emulation
     */
    public void start() {
        if (running) {
            log.warn("Emulator is already running");
            return;
        }

        log.info("Starting emulation");
        running = true;
        paused = false;

        // Загрузка ROM в память
        //loadROM();

        video.start();

        // Сброс процессора
        cpu.reset();

        // Start sound emulation
        sound.open();
        ay38912.init();
        ay38912.open();

        // Запуск главного цикла эмуляции
        startEmulationLoop();
    }

    /**
     * Stops emulation
     */
    public void stop() {
        cancelDebug();
        if (!running) {
            log.warn("Emulator is not running");
            return;
        }
        pause();
        waitForHold();
        running = false;
        video.stop();
        ula.reset();
        sound.close();
        cassetteDeck.close();
        stopEmulationThread();
        log.info("Stopping emulation");
    }

    private void stopEmulationThread() {
        if (emulationThread != null) {
            emulationThread.interrupt();
            try {
                emulationThread.join(1000);
            } catch (InterruptedException e) {
                log.error("Error while waiting for emulation thread to stop", e);
                Thread.currentThread().interrupt();
            }
            emulationThread = null;
        }
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
    }

    /**
     * Main emulation loop
     */
    private void startEmulationLoop() {
        emulationThread = new Thread(this::emulationLoop, "EmulationThread");
        emulationThread.setDaemon(true);
        emulationThread.start();
    }

    void emulationLoop() {
        log.info("Starting emulation thread");
        long lastFrameTime = System.nanoTime();

        while (running) {
            if (!paused) {
                hold = false;
                long currentTime = System.nanoTime();
                long deltaTime = currentTime - lastFrameTime;
                if (deltaTime >= FRAME_TIME_NS || speedUpMode) {
                    executeFrame();
                } else {
                    Thread.onSpinWait();
                }
            } else {
                hold = true;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Emulation thread stopped");
    }

    private void runExternalTasks() {
        Runnable task = contextsTasks.poll();
        if (task != null) {
            long startTime = System.nanoTime();
            task.run();
            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;
            if (elapsedTime > 1000000) {
                log.warn("Task took {} ms", elapsedTime / 1000000);
            }
        }
    }

    /**
     * Emulates one frame of the Spectrum
     */
    private void executeFrame() {

        long executedCycles = 0;
        while (executedCycles < machineSettings.getMachineType().tstatesFrame) {

            debugManager.preExecuteCheck(this); // hook debug event if needed

            int cycles = cpu.executeInstruction();
            if (!machineSettings.isUlaAddTStates()) {
                ula.addTStates(cycles);
            }
            executedCycles += cycles;

            video.update(cycles);

            sound.play(cycles);

            debugManager.postExecuteCheck(this);
        }
        // Render frame by hardware
        video.render();
        ula.requestInterrupt();
        clock.endFrame();
        if (!speedUpMode) {
            sound.endFrame();
            ay38912.endFrame();
        }
        frameCounter++;
        runExternalTasks();
    }

    @Override
    public void reset() {
        log.warn("Resetting emulator state...");
        pause();
        if (debugManager.isPaused()) {
            debugManager.resume();
        }
        waitForHold();
        memory.reset();
        sound.reset();
        video.reset();
        cpu.reset();
        clock.reset();
        ula.reset();
        kempston.reset();
        keyboard.reset();
        frameCounter = 0;
        setSpeedUpMode(false);
        resume();
    }

    @Override
    public Emulator getEmulator() {
        return this;
    }

    @Override
    public CPU getCPU() {
        return cpu;
    }

    @Override
    public AddressHookListener addBreakPointListener(int address, AddressHookListener listener) {
        cpu.setBreakpoint(address, true);
        return addressHookListeners.put(address, listener);
    }

    @Override
    public boolean removeBreakPointListener(int address) {
        cpu.setBreakpoint(address, false);
        addressHookListeners.remove(address);
        return true;
    }

    @Override
    public long getFrames() {
        return frameCounter;
    }

    @Override
    public void addExternalTask(Runnable task) {
        contextsTasks.add(task);
    }

    @Override
    public void loadRom(String fullName) {
        pause();
        waitForHold();
        byte[] rom = null;
        try {
            rom = EmulatorUtils.loadFile(fullName);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        getMemory().loadROM(rom);
        reset();
    }

    @Override
    public void setSpeedUpMode(boolean speedUp) {
        this.speedUpMode = speedUp;
        devices.forEach(device -> device.setSpeedUpMode(speedUp));
    }

    @SneakyThrows
    public boolean waitForHold() {
        int c = 0;
        while (!isHold()) {
            Thread.sleep(10);
            c++;
            if (c > 200) {
                log.error("Hold timeout exceeded, aborting");
                return false;
            }
        }
        return true;
    }

    private void cancelDebug() {
        if (debugManager.isPaused()) {
            debugManager.resume();
        }
    }

    public void setDebugListener(DebugListener listener) {
        debugManager.setDebugListener(listener);
    }

}
