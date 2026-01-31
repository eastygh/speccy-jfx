package spectrum.jfx.hardware.disk.wd1793.sound;

import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.disk.wd1793.ControllerState;
import spectrum.hardware.sound.Sound;

import java.net.URL;

@Slf4j
public final class FloppySoundEngineImpl implements FloppySoundEngine {

    private static final int SAMPLE_RATE = 44100;
    private static final int CPU_HZ = 3_500_000;
    private static final int MAX_SAMPLES_PER_TICK = 512;

    // Мотор крутится 2 секунды после последней активности
    private static final long MOTOR_TIMEOUT_TSTATES = 2 * CPU_HZ;

    private final Sound sound;

    private ControllerState lastState = ControllerState.IDLE;
    private long tStateAccum;
    private int sampleDebt;

    // Ресурсы
    private final short[] motorLoop;
    private int motorPos;

    // Состояние
    private boolean isMotorOn;
    private float motorVolume;
    private long motorTimer;

    private short[] pendingClick; // Текущий звук шага
    private int clickPos;
    private int pendingSteps;
    private int samplesUntilNextStep;

    private short[] transferLoop; // Текущая петля чтения/записи
    private int transferPos;

    private boolean writeMode;

    private WavLoopPlayer loopPlayer;

    public FloppySoundEngineImpl(Sound sound) {
        this.sound = sound;
        // Генерируем тяжелые данные один раз
        this.motorLoop = FloppyPcm.createMotorLoop();
        this.motorPos = 0;

        URL wavUrl = FloppyPcm.class.getResource("/sound/floppy-disk-drive-read-16.wav");
        if (wavUrl != null) {
            this.loopPlayer = new WavLoopPlayer(wavUrl);
            log.info("Floppy sound initialized: {}", wavUrl);
        } else {
            log.warn("Floppy sound wav not found: /sound/floppy-disk-drive-read-16.wav");
            this.loopPlayer = null;
        }
    }

    @Override
    public void reset() {
        tStateAccum = 0;
        sampleDebt = 0;
        isMotorOn = false;
        motorVolume = 0;
        motorTimer = 0;
        pendingClick = null;
        pendingSteps = 0;
        samplesUntilNextStep = 0;
        transferLoop = null;
        lastState = ControllerState.IDLE;
    }

    @Override
    public void init() {
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    public void ticks(long tStates, ControllerState state, boolean writeMode) {
        this.writeMode = writeMode;

        if (loopPlayer == null) {
            return;
        }

        if (state == ControllerState.IDLE) {
            if (lastState != ControllerState.IDLE) {
                loopPlayer.pause();
                log.trace("Floppy sound paused");
            }
        } else {
            if (lastState == ControllerState.IDLE) {
                loopPlayer.play(0);
                log.trace("Floppy sound playing");
            }
        }

        lastState = state;

//        // 1. Управление мотором (Spindown logic)
//        if (state != ControllerState.IDLE) {
//            isMotorOn = true;
//            motorTimer = MOTOR_TIMEOUT_TSTATES; // Сброс таймера автовыключения
//        } else if (isMotorOn) {
//            if (motorTimer > 0) {
//                motorTimer -= tStates;
//            } else {
//                isMotorOn = false; // Время вышло, мотор глохнет
//            }
//        }
//
//        // 2. Обработка времени
//        accumulateTime(tStates);
//
//        // 3. Триггеры событий
//        handleStateChange(state);
//
//        // 4. Генерация аудио
//        render();
    }

    private void accumulateTime(long tStates) {
        tStateAccum += tStates;
        long n = (tStateAccum * SAMPLE_RATE);
        int newSamples = (int) (n / CPU_HZ);
        if (newSamples > 0) {
            tStateAccum -= (long) newSamples * CPU_HZ / SAMPLE_RATE;
            sampleDebt += newSamples;
        }
    }

    @Override
    public void step(int count) {
        this.pendingSteps += count;
    }

    private void handleStateChange(ControllerState state) {
        // Логика переключений
        if (state == ControllerState.SEARCHING) {
            // При поиске чтение отключаем
            transferLoop = null;
        } else if (state == ControllerState.TRANSFERRING) {
            // Если начали передачу данных
            if (transferLoop == null) {
                transferLoop = FloppyPcm.createTransferLoop(writeMode);
                transferPos = 0;
            }
        } else if (state == ControllerState.IDLE) {
            transferLoop = null;
        }

        lastState = state;
    }

    private void render() {
        if (sampleDebt <= 0) return;

        int count = Math.min(sampleDebt, MAX_SAMPLES_PER_TICK);

        while (count-- > 0) {
            int sample = 0;

            // Слой 1: Мотор с плавным нарастанием/затуханием
            if (isMotorOn) {
                if (motorVolume < 1.0f) motorVolume += 0.0002f;
            } else {
                if (motorVolume > 0.0f) motorVolume -= 0.0001f;
            }

            if (motorVolume > 0) {
                sample += (int) (motorLoop[motorPos++] * motorVolume);
                if (motorPos >= motorLoop.length) motorPos = 0;
            }

            // Слой 2: Чтение/Запись (поверх мотора)
            if (transferLoop != null) {
                sample += transferLoop[transferPos++];
                if (transferPos >= transferLoop.length) transferPos = 0;
            }

            // Слой 3: Механический шаг (самый громкий, поверх всего)
            if (pendingClick == null && pendingSteps > 0 && samplesUntilNextStep <= 0) {
                pendingClick = FloppyPcm.createSeekClick();
                clickPos = 0;
                pendingSteps--;
                samplesUntilNextStep = (int) (0.012 * SAMPLE_RATE); // ~12мс между шагами
            }

            if (pendingClick != null) {
                sample += pendingClick[clickPos++];
                if (clickPos >= pendingClick.length) {
                    pendingClick = null;
                    clickPos = 0;
                }
            }

            if (samplesUntilNextStep > 0) {
                samplesUntilNextStep--;
            }

            // Лимитер (Hard clipping)
            if (sample > 32767) sample = 32767;
            else if (sample < -32768) sample = -32768;

            sound.write((short) sample);
            sampleDebt--;
        }
    }
}