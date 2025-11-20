package spectrum.jfx.ui.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.ui.theme.ThemeManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Data
public class AppSettings {

    // Настройки темы
    private ThemeManager.Theme theme = ThemeManager.Theme.SYSTEM;

    // Настройки эмулятора
    private int zoomLevel = 2; // X2 по умолчанию
    private boolean fastLoad = false;

    // Настройки окна
    private double windowWidth = 800;
    private double windowHeight = 600;
    private boolean windowMaximized = false;

    // Пути к файлам
    private String lastRomPath = "";
    private String lastSnapshotPath = "";

    private static final String SETTINGS_DIR = System.getProperty("user.home") + "/.spectrum-emulator";
    private static final String SETTINGS_FILE = SETTINGS_DIR + "/settings.yml";
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private static AppSettings instance;

    /**
     * Получить экземпляр настроек (Singleton)
     */
    public static AppSettings getInstance() {
        if (instance == null) {
            instance = loadSettings();
        }
        return instance;
    }

    /**
     * Загрузить настройки из файла
     */
    public static AppSettings loadSettings() {
        File settingsFile = new File(SETTINGS_FILE);

        if (settingsFile.exists()) {
            try {
                log.info("Загружаются настройки из: " + SETTINGS_FILE);
                return mapper.readValue(settingsFile, AppSettings.class);
            } catch (IOException e) {
                log.warn("Ошибка при загрузке настроек: " + e.getMessage());
                log.info("Используются настройки по умолчанию");
            }
        } else {
            log.info("Файл настроек не найден, используются настройки по умолчанию");
        }

        return new AppSettings();
    }

    /**
     * Сохранить настройки в файл
     */
    public void saveSettings() {
        try {
            // Создаем директорию если она не существует
            Path settingsDir = Paths.get(SETTINGS_DIR);
            if (!Files.exists(settingsDir)) {
                Files.createDirectories(settingsDir);
                log.info("Создана директория настроек: " + SETTINGS_DIR);
            }

            // Сохраняем настройки
            File settingsFile = new File(SETTINGS_FILE);
            mapper.writeValue(settingsFile, this);
            log.info("Настройки сохранены в: " + SETTINGS_FILE);

        } catch (IOException e) {
            log.error("Ошибка при сохранении настроек: " + e.getMessage());
        }
    }

    /**
     * Сохранить настройки темы
     */
    public void saveTheme(ThemeManager.Theme theme) {
        this.theme = theme;
        saveSettings();
    }

    /**
     * Сохранить настройки масштаба
     */
    public void saveZoomLevel(int zoomLevel) {
        this.zoomLevel = zoomLevel;
        saveSettings();
    }

    /**
     * Сохранить размеры окна
     */
    public void saveWindowSize(double width, double height, boolean maximized) {
        this.windowWidth = width;
        this.windowHeight = height;
        this.windowMaximized = maximized;
        saveSettings();
    }

    /**
     * Сохранить последний путь к ROM файлу
     */
    public void saveLastRomPath(String path) {
        this.lastRomPath = path;
        saveSettings();
    }

    /**
     * Сохранить последний путь к снэпшоту
     */
    public void saveLastSnapshotPath(String path) {
        this.lastSnapshotPath = path;
        saveSettings();
    }
}