package spectrum.jfx.ui.theme;

import javafx.scene.Scene;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
public class ThemeManager {

    @Getter
    public enum Theme {
        LIGHT("/css/light-theme.css", "Светлая"),
        DARK("/css/dark-theme.css", "Темная"),
        SYSTEM("/css/light-theme.css", "Системная");

        private final String cssPath;
        private final String displayName;

        Theme(String cssPath, String displayName) {
            this.cssPath = cssPath;
            this.displayName = displayName;
        }
    }

    private static Theme currentTheme = Theme.SYSTEM;

    /**
     * Определяет системную тему операционной системы
     */
    public static boolean isSystemDarkTheme() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("mac")) {
                return isMacOSDarkTheme();
            } else if (os.contains("win")) {
                return isWindowsDarkTheme();
            } else if (os.contains("nix") || os.contains("nux")) {
                return isLinuxDarkTheme();
            }
        } catch (Exception e) {
            log.warn("Не удалось определить системную тему: " + e.getMessage());
        }

        return false; // По умолчанию светлая тема
    }

    private static boolean isMacOSDarkTheme() {
        try {
            Process process = Runtime.getRuntime().exec("defaults read -g AppleInterfaceStyle");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            return "Dark".equalsIgnoreCase(result);
        } catch (Exception e) {
            return false; // Если команда не выполнилась, то светлая тема
        }
    }

    private static boolean isWindowsDarkTheme() {
        try {
            String command = "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) {
                    return line.contains("0x0"); // 0x0 означает темную тему
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static boolean isLinuxDarkTheme() {
        try {
            // Проверяем GTK тему
            String gtkTheme = System.getenv("GTK_THEME");
            if (gtkTheme != null && gtkTheme.toLowerCase().contains("dark")) {
                return true;
            }

            // Альтернативный способ через gsettings (для GNOME)
            Process process = Runtime.getRuntime().exec("gsettings get org.gnome.desktop.interface gtk-theme");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            if (result != null && result.toLowerCase().contains("dark")) {
                return true;
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return false;
    }

    /**
     * Применяет тему к сцене
     */
    public static void applyTheme(Scene scene, Theme theme) {
        // Очищаем предыдущие стили
        scene.getStylesheets().clear();

        String cssPath;
        if (theme == Theme.SYSTEM) {
            cssPath = isSystemDarkTheme() ? Theme.DARK.getCssPath() : Theme.LIGHT.getCssPath();
            log.info("Применение системной темы: " + (isSystemDarkTheme() ? "темная" : "светлая"));
        } else {
            cssPath = theme.getCssPath();
            log.info("Применение темы: " + theme.getDisplayName());
        }

        // Добавляем CSS файл
        try {
            String css = ThemeManager.class.getResource(cssPath).toExternalForm();
            scene.getStylesheets().add(css);
            currentTheme = theme;
        } catch (Exception e) {
            log.error("Ошибка при загрузке темы: " + e.getMessage());
            // Fallback к базовой теме JavaFX
        }
    }

    /**
     * Применяет текущую тему к диалоговому окну
     */
    public static void applyThemeToDialog(javafx.scene.control.Dialog<?> dialog) {
        if (dialog.getDialogPane().getScene() != null) {
            applyCurrentTheme(dialog.getDialogPane().getScene());
        }
    }

    /**
     * Применяет текущую активную тему к сцене
     */
    public static void applyCurrentTheme(Scene scene) {
        applyTheme(scene, currentTheme);
    }

    /**
     * Получает текущую активную тему
     */
    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Переключает на следующую тему
     */
    public static Theme getNextTheme() {
        Theme[] themes = Theme.values();
        int currentIndex = currentTheme.ordinal();
        int nextIndex = (currentIndex + 1) % themes.length;
        return themes[nextIndex];
    }
}