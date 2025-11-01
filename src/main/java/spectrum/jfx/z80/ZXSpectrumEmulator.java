package spectrum.jfx.z80;


import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.z80.cpu.Z80CPU;
import spectrum.jfx.z80.input.Keyboard;
import spectrum.jfx.z80.memory.Memory;
import spectrum.jfx.z80.memory.MemoryImpl;
import spectrum.jfx.z80.sound.Sound;
import spectrum.jfx.z80.video.Video;
import spectrum.jfx.z80.video.VideoImpl;

/**
 * Основной класс эмулятора ZX Spectrum
 * Координирует работу всех компонентов системы
 */
@Getter
public class ZXSpectrumEmulator {

    private static final Logger logger = LoggerFactory.getLogger(ZXSpectrumEmulator.class);

    // Компоненты системы
    private final Z80CPU cpu;
    private final Memory memory;
    private final Video video;
    private final Keyboard keyboard;
    private final Sound sound;

    // Состояние эмуляции
    private boolean running;
    private boolean paused;

    // Настройки тактовой частоты
    private static final long CPU_FREQUENCY_HZ = 3500000; // 3.5 MHz для ZX Spectrum 48K
    private static final long FRAME_TIME_NS = 16666666; // ~60 FPS

    public ZXSpectrumEmulator() {
        logger.info("Initializing ZX Spectrum Emulator");

        // Инициализация компонентов
        this.memory = new MemoryImpl();
        this.video = new VideoImpl(memory);
        this.keyboard = new Keyboard();
        this.sound = new Sound();
        this.cpu = new Z80CPU(memory);

        this.running = false;
        this.paused = false;

        logger.info("ZX Spectrum Emulator initialized successfully");
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
     * Загружает ROM в память
     */
    private void loadROM() {
        try {
            logger.info("Loading ZX Spectrum 48K ROM");
            // TODO: Реализовать загрузку ROM файла
            // Пока что создаем простой тестовый ROM с базовыми командами
            byte[] rom = new byte[16384]; // 16K ROM

            // Заполняем ROM простыми инструкциями для демонстрации
            // Адрес 0x0000: JP 0x0003 (переход на адрес 3)
            rom[0] = (byte) 0xC3; // JP
            rom[1] = (byte) 0x03; // низший байт адреса
            rom[2] = (byte) 0x00; // старший байт адреса

            // Адрес 0x0003: NOP, NOP, HALT
            rom[3] = (byte) 0x00; // NOP
            rom[4] = (byte) 0x00; // NOP
            rom[5] = (byte) 0x76; // HALT

            //memory.loadROM(rom);
            logger.info("Test ROM loaded successfully (16384 bytes)");
        } catch (Exception e) {
            logger.error("Failed to load ROM", e);
            // Создаем минимальный ROM даже если загрузка не удалась
            byte[] emptyRom = new byte[16384];
            emptyRom[0] = (byte) 0x76; // HALT на первой позиции
            //memory.loadROM(emptyRom);
            logger.info("Fallback empty ROM loaded");
        }
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
            int cycles = cpu.executeInstruction();
            executedCycles += cycles;

            // Обновляем видеосистему
            video.update(cycles);

            // Обновляем звуковую систему
            sound.update(cycles);
        }

        // Рендеринг кадра
        video.render();
    }

}