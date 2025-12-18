package spectrum.jfx.hardware.sound;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.OutPortListener;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Эмуляция звуковой системы ZX Spectrum
 * <p>
 * ZX Spectrum имеет простую звуковую систему - только один динамик (beeper),
 * управляемый битом 4 порта 0xFE. Состояние динамика может быть:
 * 0 - выключен (низкий уровень)
 * 1 - включен (высокий уровень)
 */
public class Sound implements OutPortListener, ClockListener {
    private static final Logger logger = LoggerFactory.getLogger(Sound.class);

    // Параметры аудио
    private static final int SAMPLE_RATE = 44100;     // 44.1 kHz
    private static final int BUFFER_SIZE = 1024;      // Размер буфера
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            SAMPLE_RATE, 16, 1, true, false
    );

    // Состояние звуковой системы
    private boolean beeperState = false;
    private boolean soundEnabled = true;
    /**
     * -- GETTER --
     * Получает текущую громкость
     */
    @Getter
    private float volume = 0.5f;

    // Аудио компоненты
    private SourceDataLine audioLine;
    private final BlockingQueue<Short> audioBuffer = new ArrayBlockingQueue<>(BUFFER_SIZE * 2);
    private Thread audioThread;
    private volatile boolean audioThreadRunning = false;

    // Состояние для генерации сэмплов
    private long totalCycles = 0;
    private long lastBeeperChange = 0;

    // Константы для расчетов
    private static final long CPU_FREQUENCY = 3500000;  // 3.5 MHz
    private static final double CYCLES_PER_SAMPLE = (double) CPU_FREQUENCY / SAMPLE_RATE;
    private volatile long tStates = 0;

    public Sound() {
        logger.info("Initializing ZX Spectrum sound system");
        initializeAudio();
        startAudioThread();
        logger.info("Sound system initialized");
    }

    /**
     * Инициализирует аудио систему
     */
    private void initializeAudio() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);

            if (!AudioSystem.isLineSupported(info)) {
                logger.warn("Audio line not supported, sound will be disabled");
                soundEnabled = false;
                return;
            }

            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(AUDIO_FORMAT, BUFFER_SIZE * 2);
            audioLine.start();

            logger.info("Audio line initialized: {} Hz, {} bit, {} channel",
                    AUDIO_FORMAT.getSampleRate(), AUDIO_FORMAT.getSampleSizeInBits(),
                    AUDIO_FORMAT.getChannels());

        } catch (LineUnavailableException e) {
            logger.warn("Audio line unavailable, sound will be disabled: {}", e.getMessage());
            soundEnabled = false;
        } catch (Exception e) {
            logger.warn("Failed to initialize audio, sound will be disabled: {}", e.getMessage());
            soundEnabled = false;
        }
    }

    /**
     * Запускает поток для воспроизведения аудио
     */
    private void startAudioThread() {
        if (!soundEnabled) {
            return;
        }

        audioThreadRunning = true;
        audioThread = new Thread(this::audioThreadLoop, "AudioThread");
        audioThread.setDaemon(true);
        audioThread.start();

        logger.debug("Audio thread started");
    }

    /**
     * Главный цикл аудио потока
     */
    private void audioThreadLoop() {
        byte[] buffer = new byte[BUFFER_SIZE * 2]; // 16-bit samples
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE * 2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        while (audioThreadRunning) {
            try {
                byteBuffer.clear();

                // Заполняем буфер сэмплами
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    Short sample = audioBuffer.take(); // Блокирующий вызов
                    byteBuffer.putShort(sample);
                }

                // Воспроизводим буфер
                byteBuffer.rewind();
                byteBuffer.get(buffer);
                audioLine.write(buffer, 0, buffer.length);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in audio thread", e);
                break;
            }
        }

        logger.debug("Audio thread stopped");
    }

    @Override
    public void outPort(int port, int value) {
        if ((port & 0xFF) != 0xFE) {
            return;
        }
        boolean newBeeperState = (value & 0x10) != 0;
        if (newBeeperState != beeperState) {
            beeperState = newBeeperState;
            lastBeeperChange = tStates;
        }
    }

    @Override
    public void ticks(long tStates, int delta) {
        this.tStates = tStates;
    }

    /**
     * Обновляет звуковую систему
     *
     * @param cycles количество тактов процессора
     */
    public void update(int cycles) {
        if (!soundEnabled) {
            return;
        }

        totalCycles += cycles;

        // Вычисляем, сколько аудио сэмплов нужно сгенерировать
        long samplesNeeded = (long) (totalCycles / CYCLES_PER_SAMPLE);
        long currentSamples = audioBuffer.size();

        for (long i = currentSamples; i < samplesNeeded; i++) {
            short sample = generateSample();

            // Добавляем сэмпл в буфер (неблокирующий)
            if (!audioBuffer.offer(sample)) {
                // Буфер переполнен, пропускаем сэмпл
                logger.debug("Audio buffer overflow, dropping sample");
                break;
            }
        }
    }

    /**
     * Генерирует один аудио сэмпл
     */
    private short generateSample() {
        // Простая генерация: если beeper включен - максимальная амплитуда,
        // если выключен - тишина
        if (beeperState) {
            return (short) (Short.MAX_VALUE * volume);
        } else {
            return (short) (Short.MIN_VALUE * volume);
        }
    }

    /**
     * Устанавливает громкость звука
     *
     * @param volume громкость от 0.0 до 1.0
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        logger.debug("Volume set to: {}", this.volume);
    }

    /**
     * Включает/выключает звук
     */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;

        if (!enabled && audioLine != null) {
            audioLine.flush();
        }

        logger.info("Sound {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Создает короткий звуковой сигнал (beep)
     *
     * @param frequency частота в Герцах
     * @param duration  продолжительность в миллисекундах
     */
    public void playBeep(int frequency, int duration) {
        if (!soundEnabled) {
            return;
        }

        // Создаем отдельный поток для воспроизведения beep
        Thread beepThread = new Thread(() -> {
            try {
                generateBeep(frequency, duration);
            } catch (Exception e) {
                logger.error("Error playing beep", e);
            }
        }, "BeepThread");

        beepThread.setDaemon(true);
        beepThread.start();
    }

    /**
     * Генерирует звуковой сигнал заданной частоты и продолжительности
     */
    private void generateBeep(int frequency, int duration) throws LineUnavailableException {
        int sampleCount = (int) ((long) SAMPLE_RATE * duration / 1000);
        byte[] buffer = new byte[sampleCount * 2]; // 16-bit samples

        double angleStep = 2.0 * Math.PI * frequency / SAMPLE_RATE;

        for (int i = 0; i < sampleCount; i++) {
            double angle = i * angleStep;
            short sample = (short) (Short.MAX_VALUE * Math.sin(angle) * volume);

            // Little-endian format
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        // Временно создаем отдельную линию для beep
        SourceDataLine beepLine = (SourceDataLine) AudioSystem.getLine(
                new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT));
        beepLine.open(AUDIO_FORMAT);
        beepLine.start();
        beepLine.write(buffer, 0, buffer.length);
        beepLine.drain();
        beepLine.close();
    }

    /**
     * Останавливает звуковую систему
     */
    public void stop() {
        logger.info("Stopping sound system");

        audioThreadRunning = false;

        if (audioThread != null) {
            audioThread.interrupt();
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }

        logger.info("Sound system stopped");
    }

    /**
     * Очищает аудио буфер
     */
    public void clearBuffer() {
        audioBuffer.clear();
        if (audioLine != null) {
            audioLine.flush();
        }
        logger.debug("Audio buffer cleared");
    }

    /**
     * Получает информацию о состоянии звуковой системы
     */
    public String getStatusInfo() {
        return String.format(
                "Sound: %s, Volume: %.1f%%, Beeper: %s, Buffer: %d/%d",
                soundEnabled ? "ON" : "OFF",
                volume * 100,
                beeperState ? "ON" : "OFF",
                audioBuffer.size(),
                audioBuffer.remainingCapacity() + audioBuffer.size()
        );
    }

    /**
     * Сохраняет аудио в WAV файл (для отладки)
     */
    public void saveToWavFile(String filename, byte[] audioData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = new AudioInputStream(bais, AUDIO_FORMAT, audioData.length / 2);
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, new java.io.File(filename));
            logger.info("Audio saved to: {}", filename);
        } catch (IOException e) {
            logger.error("Failed to save audio to file: {}", filename, e);
        }
    }
}