package spectrum.jfx.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import spectrum.jfx.ui.controller.MainController;
import spectrum.jfx.ui.theme.ThemeManager;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.hardware.SpectrumEmulator;

import java.io.IOException;

import static spectrum.jfx.hardware.util.EmulatorUtils.loadFile;
import static spectrum.jfx.hardware.video.ZoomLevel.X2;

public class GUIApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        // Загружаем сохраненные настройки
        AppSettings settings = AppSettings.getInstance();
        LocalizationManager localizationManager = LocalizationManager.getInstance();

        FXMLLoader fxmlLoader = new FXMLLoader(GUIApplication.class.getResource("/main-view.fxml"));
        // Устанавливаем resource bundle для загрузчика FXML
        fxmlLoader.setResources(java.util.ResourceBundle.getBundle("messages", localizationManager.getCurrentLanguage().getLocale()));
        BorderPane root = fxmlLoader.load();

        SpectrumEmulator emulator = new SpectrumEmulator();
        emulator.init();
        emulator.getVideo().setZoomLevel(X2);

        // Получаем контроллер и передаем ему ссылку на эмулятор
        MainController controller = fxmlLoader.getController();
        controller.setEmulator(emulator);

        // Рассчитываем размер окна с учетом всех UI элементов
        // Canvas включает в себя полный размер: экран + две рамки с каждой стороны
        double videoWidth = (emulator.getVideo().getScaledScreenWidth() + 2 * emulator.getVideo().getScaledBorderSize());
        double videoHeight = (emulator.getVideo().getScaledScreenHeight() + 2 * emulator.getVideo().getScaledBorderSize());

        // Используем сохраненные размеры окна или рассчитанные по умолчанию
        double width = settings.isWindowMaximized() ? settings.getWindowWidth() : videoWidth + 40.0;
        double height = settings.isWindowMaximized() ? settings.getWindowHeight() : videoHeight + 40.0 + 100.0;

        // Отладочный вывод
        System.out.println("Video canvas size: " + videoWidth + "x" + videoHeight);
        System.out.println("Window size: " + width + "x" + height);

        Scene scene = new Scene(root, width, height);

        // Применяем сохраненную тему
        ThemeManager.applyTheme(scene, settings.getTheme());

        // Устанавливаем соответствующую радиокнопку темы
        switch (settings.getTheme()) {
            case LIGHT:
                controller.getThemeLightMenuItem().setSelected(true);
                break;
            case DARK:
                controller.getThemeDarkMenuItem().setSelected(true);
                break;
            default:
                controller.getThemeSystemMenuItem().setSelected(true);
                break;
        }

        // Передаем ссылку на сцену в контроллер для переключения тем
        controller.setScene(scene);

        stage.setTitle(localizationManager.getString("app.title"));
        stage.setScene(scene);

        // Устанавливаем минимальный размер окна
        stage.setMinWidth(videoWidth + 40.0);
        stage.setMinHeight(videoHeight + 40.0 + 100.0);

        // Разрешаем изменение размера окна
        stage.setResizable(true);

        // Восстанавливаем состояние окна
        if (settings.isWindowMaximized()) {
            stage.setMaximized(true);
        }

        // Сохраняем размер окна при изменении
        stage.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (!stage.isMaximized()) {
                settings.saveWindowSize(newWidth.doubleValue(), stage.getHeight(), false);
            }
        });

        stage.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (!stage.isMaximized()) {
                settings.saveWindowSize(stage.getWidth(), newHeight.doubleValue(), false);
            }
        });

        // Сохраняем состояние максимизации
        stage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            settings.saveWindowSize(stage.getWidth(), stage.getHeight(), isMaximized);
        });

        stage.show();

        scene.setOnKeyPressed(event -> emulator.getKeyboard().keyPressed(event.getCode()));
        scene.setOnKeyReleased(event -> emulator.getKeyboard().keyReleased(event.getCode()));

        controller.getVideoContainer().getChildren().add((Node) emulator.getVideo().getCanvas());

        byte[] rom = loadFile("/roms/48.rom");
        emulator.getMemory().loadROM(rom);
        emulator.start();

    }

}
