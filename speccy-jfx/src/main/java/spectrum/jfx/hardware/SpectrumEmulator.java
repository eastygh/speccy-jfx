package spectrum.jfx.hardware;

import lombok.Getter;
import machine.MachineTypes;
import machine.SpectrumClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.hardware.cpu.BreakPointListener;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.cpu.Z80WrapperImpl;
import spectrum.jfx.hardware.input.GamePadGLFWImpl;
import spectrum.jfx.hardware.input.Kempston;
import spectrum.jfx.hardware.input.KempstonImpl;
import spectrum.jfx.hardware.input.Keyboard;
import spectrum.jfx.hardware.machine.Emulator;
import spectrum.jfx.hardware.machine.HardwareProvider;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.memory.MemoryImpl;
import spectrum.jfx.hardware.sound.OneThreadAudioImpl;
import spectrum.jfx.hardware.sound.Sound;
import spectrum.jfx.hardware.tape.CassetteDeckImpl;
import spectrum.jfx.hardware.ula.*;
import spectrum.jfx.hardware.util.EmulatorUtils;
import spectrum.jfx.hardware.video.ScanlineVideoImpl;
import spectrum.jfx.hardware.video.Video;
import spectrum.jfx.machine.Machine;
import z80core.NotifyOps;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public class SpectrumEmulator implements NotifyOps, HardwareProvider, Emulator {

    private static final Logger logger = LoggerFactory.getLogger(SpectrumEmulator.class);

    // ZXCore-lib clock support
    SpectrumClock clock = SpectrumClock.INSTANCE;

    CPU cpu;
    Memory memory;
    Video<?> video;
    Keyboard keyboard;
    Sound sound;
    Ula ula;
    CassetteDeckImpl cassetteDeck;
    Kempston kempston;


    // Emulation management
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean speedUpMode = false;

    // Emulation state
    private volatile boolean hold = false;
    private volatile long frameCounter = 0;

    // Настройки тактовой частоты
    private static final long CPU_FREQUENCY_HZ = 3500000; // 3.5 MHz для ZX Spectrum 48K
    private static final long FRAME_TIME_NS = 16666666; // ~60 FPS

    private final Map<Integer, BreakPointListener> breakPoints = new ConcurrentHashMap<>();
    private final Queue<Runnable> contextsTasks = new ConcurrentLinkedQueue<>();

    public SpectrumEmulator() {

    }

    public void init(MachineTypes machineType) {

        SpectrumClock.INSTANCE.setSpectrumModel(machineType);

        this.memory = new MemoryImpl();

        this.video = new ScanlineVideoImpl(memory);
        this.keyboard = new Keyboard();
        this.keyboard.resetKeyboard();
        this.ula = new UlaImpl(memory);
        this.ula.addPortListener(0xfe, keyboard); // keyboard
        this.ula.addPortListener(0xfe, (OutPortListener) video); // Border color

        this.sound = new OneThreadAudioImpl(MachineTypes.SPECTRUM48K, 44100); // Sound
        this.ula.addPortListener(0xfe, sound); // Sound
        this.ula.addClockListener(sound);

        this.cassetteDeck = new CassetteDeckImpl();
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
        this.ula.addPortListener(0x1F, kempston);
        this.kempston.init();

        cpu = new Z80WrapperImpl(ula, this);

        Machine.setHardwareProvider(this);

    }

    @Override
    public int breakpoint(int address, int opcode) {
        BreakPointListener listener = breakPoints.get(address);
        if (listener != null) {
            return listener.call(address, opcode);
        }
        return opcode;
    }

    @Override
    public void execDone() {

    }


    /**
     * Запускает эмуляцию
     */
    public void start() {
        if (running) {
            logger.warn("Emulator is already running");
            return;
        }

        logger.info("Starting emulation");
        running = true;
        paused = false;

        // Загрузка ROM в память
        //loadROM();

        video.start();

        // Сброс процессора
        cpu.reset();

        // Start sound emulation
        sound.open();

        // Запуск главного цикла эмуляции
        startEmulationLoop();
    }

    /**
     * Останавливает эмуляцию
     */
    public void stop() {
        if (!running) {
            logger.warn("Emulator is not running");
            return;
        }
        pause();
        running = false;
        video.stop();
        ula.reset();
        sound.close();
        logger.info("Stopping emulation");
    }

    /**
     * Приостанавливает/возобновляет эмуляцию
     */
    public void togglePause() {
        if (!running) {
            logger.warn("Cannot pause - emulator is not running");
            return;
        }

        paused = !paused;
        logger.info(paused ? "Emulation paused" : "Emulation resumed");
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
        Thread emulationThread = new Thread(() -> {
            logger.info("Starting emulation thread");
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
                    runExternalTasks();
                    hold = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            logger.info("Emulation thread stopped");
        }, "EmulationThread");

        emulationThread.setDaemon(true);
        emulationThread.start();
    }


    private void runExternalTasks() {
        Runnable task = contextsTasks.poll();
        if (task != null) {
            long startTime = System.nanoTime();
            task.run();
            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;
            if (elapsedTime > 1000000) {
                logger.warn("Task took {} ms", elapsedTime / 1000000);
            }
        }
    }

    /**
     * Emulates one frame of the Spectrum
     */
    private void executeFrame() {
        // Количество тактов процессора за кадр
        long cyclesPerFrame = CPU_FREQUENCY_HZ / 60;

        long executedCycles = 0;
        while (executedCycles < cyclesPerFrame) {

            // Выполняем одну инструкцию процессора
            int cycles = cpu.executeInstruction();
            executedCycles += cycles;

            // Обновляем видеосистему
            video.update(cycles);

            // Обновляем звуковую систему
            sound.play(cycles);
        }
        // Рендеринг кадра
        video.render();
        ula.requestInterrupt();
        frameCounter++;
        clock.endFrame();
        if (!speedUpMode) {
            sound.endFrame();
        }
        runExternalTasks();
    }

    @Override
    public void reset() {
        pause();
        while (!isHold()) {
            Thread.yield();
        }
        memory.reset();
        sound.reset();
        video.reset();
        cpu.reset();
        clock.reset();
        ula.reset();
        kempston.reset();
        keyboard.reset();
        frameCounter = 0;
        speedUpMode = false;
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
    public BreakPointListener addBreakPointListener(int address, BreakPointListener listener) {
        cpu.setBreakpoint(address, true);
        return breakPoints.put(address, listener);
    }

    @Override
    public boolean removeBreakPointListener(int address) {
        cpu.setBreakpoint(address, false);
        breakPoints.remove(address);
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
    }

}
