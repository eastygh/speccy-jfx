package spectrum.jfx.z80;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.z80.input.Keyboard;
import spectrum.jfx.z80.memory.Memory;
import spectrum.jfx.z80.memory.MemoryImpl;
import spectrum.jfx.z80.sound.Sound;
import spectrum.jfx.z80.ula.Ula;
import spectrum.jfx.z80.ula.UlaImpl;
import spectrum.jfx.z80.video.Video;
import spectrum.jfx.z80.video.VideoImpl;
import spectrum.jfx.z80core.NotifyOps;
import spectrum.jfx.z80core.Z80;

@Getter
public class SpectrumEmulator implements NotifyOps {

    private static final Logger logger = LoggerFactory.getLogger(SpectrumEmulator.class);

    Z80 cpu;
    Memory memory;
    Video video;
    Keyboard keyboard;
    Sound sound;
    Ula ula;

    // Состояние эмуляции
    private boolean running;
    private boolean paused;

    // Настройки тактовой частоты
    private static final long CPU_FREQUENCY_HZ = 3500000; // 3.5 MHz для ZX Spectrum 48K
    private static final long FRAME_TIME_NS = 16666666; // ~60 FPS

    public SpectrumEmulator() {

    }

    public void init() {
        this.memory = new MemoryImpl();
        this.video = new VideoImpl(memory);
        this.keyboard = new Keyboard();
        this.sound = new Sound();
        this.keyboard.resetKeyboard();
        this.ula = new UlaImpl(memory);
        this.ula.addPortListener((byte) 0xfe, keyboard);
        cpu = new Z80(ula, this);
    }

    @Override
    public int breakpoint(int address, int opcode) {
        return 0;
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

        // Сброс процессора
        cpu.reset();

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

        logger.info("Stopping emulation");
        running = false;
        paused = false;
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

    /**
     * Главный цикл эмуляции
     */
    private void startEmulationLoop() {
        Thread emulationThread = new Thread(() -> {
            logger.info("Starting emulation thread");
            long lastFrameTime = System.nanoTime();

            while (running) {
                if (!paused) {
                    long currentTime = System.nanoTime();
                    long deltaTime = currentTime - lastFrameTime;

                    if (deltaTime >= FRAME_TIME_NS) {
                        // Выполняем один кадр эмуляции
                        executeFrame();
                        lastFrameTime = currentTime;
                    }
                } else {
                    // Если на паузе, ждем немного
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

    /**
     * Выполняет эмуляцию одного кадра
     */
    private void executeFrame() {
        // Количество тактов процессора за кадр
        long cyclesPerFrame = CPU_FREQUENCY_HZ / 60;

        long executedCycles = 0;
        while (executedCycles < cyclesPerFrame && running && !paused) {
            // Выполняем одну инструкцию процессора
            long startCycles = ula.gettStates();
            cpu.execute();
            int cycles = (int) (ula.gettStates() - startCycles);
            executedCycles += cycles;

            // Обновляем видеосистему
            video.update(cycles);

            // Обновляем звуковую систему
            sound.update(cycles);
        }
        // Рендеринг кадра
        video.render();
        ula.requestInterrupt();
    }

}
