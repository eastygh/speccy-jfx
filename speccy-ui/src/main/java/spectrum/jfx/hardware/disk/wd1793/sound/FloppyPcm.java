package spectrum.jfx.hardware.disk.wd1793.sound;

import java.io.File;
import java.util.Objects;
import java.util.Random;

public final class FloppyPcm {

    private static final Random rnd = new Random();
    private static final int SAMPLE_RATE = 44100;

    private FloppyPcm() {
    }

    public static short[] floppyRead =
            FloppyWav.loadTrimmed(new File(Objects.requireNonNull(FloppyPcm.class.getResource("/sound/floppy-disk-drive-read-16.wav")).getFile()));

    /**
     * SEEK: Резкий удар шагового двигателя.
     * Это не просто "клик", это "ТыК".
     * Используем квадратную волну для имитации работы соленоида.
     */
    public static short[] createSeekClick() {
        // Увеличим длительность для более сочного звука механики
        int len = (int) (0.015 * SAMPLE_RATE);
        short[] buffer = new short[len];

        for (int i = 0; i < len; i++) {
            double t = (double) i / SAMPLE_RATE;
            // Резкая экспоненциальная огибающая
            double env = Math.exp(-i * 10.0 / len);

            // Низкочастотный удар (корпус)
            double low = Math.sin(2 * Math.PI * 150 * t);
            // Высокочастотный металлический щелчок
            double high = (rnd.nextDouble() * 2 - 1) * 0.5;
            
            double val = (low * 0.6 + high * 0.4) * 28000 * env;
            buffer[i] = clip(val);
        }
        return buffer;
    }

    /**
     * TRANSFER: Звук чтения/записи.
     * Сделаем его как высокочастотное "шуршание" головок.
     */
    public static short[] createTransferLoop(boolean write) {
        int len = (int) (0.05 * SAMPLE_RATE);
        short[] buffer = new short[len];

        for (int i = 0; i < len; i++) {
            // Белый шум с полосовым фильтром (имитация)
            // Просто берем шум и ограничиваем его резкость
            double noise = (rnd.nextDouble() * 2 - 1);
            
            // Модулируем амплитуду, чтобы был "стрекот"
            double mod = 0.5 + 0.5 * Math.sin(2 * Math.PI * 1000 * ((double)i / SAMPLE_RATE));
            
            double val = noise * mod * (write ? 4000 : 2500);
            buffer[i] = clip(val);
        }
        return buffer;
    }

    /**
     * MOTOR: Шелест вращающегося диска.
     * Убираем 100Гц гул, оставляем мягкий шум и 5Гц пульсацию.
     */
    public static short[] createMotorLoop() {
        int len = SAMPLE_RATE; // 1 секунда
        short[] buffer = new short[len];

        for (int i = 0; i < len; i++) {
            double t = (double) i / SAMPLE_RATE;

            // Мягкий шум (основа шелеста)
            double noise = (rnd.nextDouble() * 2 - 1) * 0.3;
            
            // Очень тихий низкий рокот (вибрация)
            double rumble = Math.sin(2 * Math.PI * 60 * t) * 0.2;
            
            // Модуляция 5Гц (вращение 300 RPM)
            double rot = 0.8 + 0.2 * Math.sin(2 * Math.PI * 5 * t);

            double val = (noise + rumble) * rot * 2000;
            buffer[i] = clip(val);
        }

        // Сглаживание краев петли
        for (int i = 0; i < 200; i++) {
            double f = (double) i / 200;
            buffer[len - 1 - i] *= f;
            buffer[i] *= f;
        }

        return buffer;
    }

    private static short clip(double val) {
        if (val > 32767) return 32767;
        if (val < -32768) return -32768;
        return (short) val;
    }
}