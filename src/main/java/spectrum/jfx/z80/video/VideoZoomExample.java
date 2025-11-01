package spectrum.jfx.z80.video;


import spectrum.jfx.z80.memory.Memory;
import spectrum.jfx.z80.memory.MemoryImpl;

/**
 * Пример использования масштабирования в Video системе
 * Демонстрирует все новые возможности масштабирования
 */
public class VideoZoomExample {

    public static void main(String[] args) {
        // Создаем объекты
        Memory memory = new MemoryImpl();
        Video video = new VideoImpl(memory); // По умолчанию x2 масштаб

        System.out.println("=== Пример использования масштабирования Video ===");
        System.out.println();

        // Показываем текущее состояние
        printVideoInfo(video, "Начальное состояние");

        // Создаем небольшой тестовый паттерн в видеопамяти
        createTestPattern(memory);
        video.markScreenDirty();
        video.render();

        System.out.println("Тестовый паттерн создан в видеопамяти.");
        System.out.println();

        // Демонстрируем переключение масштабов
        System.out.println("=== Переключение масштабов ===");

        // Переходим на x1
        video.setZoomLevel(ZoomLevel.X1);
        printVideoInfo(video, "Масштаб x1");

        // Переходим на x4
        video.setZoomLevel(ZoomLevel.X4);
        printVideoInfo(video, "Масштаб x4");

        // Демонстрируем циклическое переключение
        System.out.println("=== Циклическое переключение ===");

        video.nextZoomLevel(); // Должен перейти на x1 (после x4)
        printVideoInfo(video, "После nextZoomLevel()");

        video.nextZoomLevel(); // Должен перейти на x2
        printVideoInfo(video, "После второго nextZoomLevel()");

        video.previousZoomLevel(); // Должен вернуться на x1
        printVideoInfo(video, "После previousZoomLevel()");

        // Показываем все доступные уровни масштабирования
        System.out.println("=== Доступные уровни масштабирования ===");
        ZoomLevel[] levels = video.getAvailableZoomLevels();
        for (ZoomLevel level : levels) {
            System.out.printf("%s: масштаб %dx, размер canvas %dx%d\n",
                    level.getDisplayName(),
                    level.getScale(),
                    video.getTotalWidth() * level.getScale(),
                    video.getTotalHeight() * level.getScale());
        }

        System.out.println();
        System.out.println("=== Сравнение размеров ===");
        System.out.printf("Оригинальные размеры экрана: %dx%d\n",
                video.getScreenWidth(), video.getScreenHeight());
        System.out.printf("Оригинальный общий размер: %dx%d\n",
                video.getTotalWidth(), video.getTotalHeight());
        System.out.println();

        // Показываем масштабированные размеры для каждого уровня
        for (ZoomLevel level : levels) {
            video.setZoomLevel(level);
            System.out.printf("При масштабе %s:\n", level.getDisplayName());
            System.out.printf("  Размер экрана: %dx%d\n",
                    video.getScaledScreenWidth(), video.getScaledScreenHeight());
            System.out.printf("  Общий размер: %dx%d\n",
                    video.getScaledTotalWidth(), video.getScaledTotalHeight());
            System.out.printf("  Размер рамки: %d\n", video.getScaledBorderSize());
//            System.out.printf("  Canvas: %.0fx%.0f\n",
//                    video.getCanvas().getWidth(), video.getCanvas().getHeight());
            System.out.println();
        }

        System.out.println("=== Практические советы ===");
        System.out.println("• x1 (256x192) - оригинальный размер, может быть слишком мелким");
        System.out.println("• x2 (512x384) - оптимальный размер для большинства случаев");
        System.out.println("• x4 (1024x768) - большой размер для детального просмотра");
        System.out.println();
        System.out.println("Для интеграции в GUI:");
        System.out.println("• Используйте video.getCanvas() для получения JavaFX Canvas");
        System.out.println("• Добавьте горячие клавиши для video.nextZoomLevel()");
        System.out.println("• Используйте getScaled*() методы для размещения окна");
        System.out.println();
        System.out.println("=== Video масштабирование готово к использованию! ===");
    }

    /**
     * Выводит информацию о текущем состоянии Video
     */
    private static void printVideoInfo(Video video, String title) {
        System.out.println("--- " + title + " ---");
//        System.out.printf("Текущий масштаб: %s (x%d)\n",
//                video.getZoomDisplayName(), video.getCurrentScale());
//        System.out.printf("Размер canvas: %.0fx%.0f пикселей\n",
//                video.getCanvas().getWidth(), video.getCanvas().getHeight());
        System.out.printf("Масштабированный экран: %dx%d\n",
                video.getScaledScreenWidth(), video.getScaledScreenHeight());
        System.out.printf("Масштабированная рамка: %d пикселей\n",
                video.getScaledBorderSize());
        System.out.println();
    }

    /**
     * Создает простой тестовый паттерн в видеопамяти
     */
    private static void createTestPattern(Memory memory) {
        // Создаем диагональные линии в bitmap области
        for (int y = 0; y < 192; y += 8) {
            for (int x = 0; x < 256; x += 8) {
                int bitmapAddr = 0x4000 + (y / 8) * 32 + (x / 8);
                if (bitmapAddr < 0x5800) {
                    // Создаем диагональный паттерн
                    memory.writeByte(bitmapAddr, 0b10001000);
                }
            }
        }

        // Устанавливаем атрибуты - белые пиксели на черном фоне
        for (int addr = 0x5800; addr < 0x5B00; addr++) {
            memory.writeByte(addr, 0x07); // Белые чернила, черная бумага
        }

        // Добавляем немного цвета в центр
        for (int y = 8; y < 16; y++) {
            for (int x = 12; x < 20; x++) {
                int attrAddr = 0x5800 + y * 32 + x;
                memory.writeByte(attrAddr, 0x46); // Красные чернила на желтой бумаге + BRIGHT
            }
        }
    }
}