package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import spectrum.jfx.hardware.SpectrumEmulator;
import spectrum.jfx.ui.theme.ThemeManager;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.controller.TapeLibraryController;

import java.io.File;
import java.io.IOException;

@Getter
@Setter
public class MainController {

    @FXML
    public VBox videoContainer;

    @FXML
    private MenuBar menuBar;
    @FXML
    private ToolBar toolBar;

    // Menu items
    @FXML
    private MenuItem openRomMenuItem;
    @FXML
    private MenuItem openSnapshotMenuItem;
    @FXML
    private MenuItem saveSnapshotMenuItem;
    @FXML
    private MenuItem tapeLibraryMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private MenuItem pauseMenuItem;
    @FXML
    private MenuItem resetMenuItem;
    @FXML
    private MenuItem fastLoadMenuItem;
    @FXML
    private RadioMenuItem zoom1MenuItem;
    @FXML
    private RadioMenuItem zoom2MenuItem;
    @FXML
    private RadioMenuItem zoom3MenuItem;
    @FXML
    private RadioMenuItem themeSystemMenuItem;
    @FXML
    private RadioMenuItem themeLightMenuItem;
    @FXML
    private RadioMenuItem themeDarkMenuItem;
    @FXML
    private MenuItem settingsMenuItem;
    @FXML
    private MenuItem aboutMenuItem;

    // Toolbar buttons
    @FXML
    private Button openRomButton;
    @FXML
    private Button pauseButton;
    @FXML
    private Button resetButton;
    @FXML
    private Button snapshotButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button settingsButton;

    private SpectrumEmulator emulator;
    private Scene scene;
    private boolean isPaused = false;

    public void initialize() {
        // Группировка радиокнопок масштаба
        ToggleGroup zoomGroup = new ToggleGroup();
        zoom1MenuItem.setToggleGroup(zoomGroup);
        zoom2MenuItem.setToggleGroup(zoomGroup);
        zoom3MenuItem.setToggleGroup(zoomGroup);

        // Группировка радиокнопок темы
        ToggleGroup themeGroup = new ToggleGroup();
        themeSystemMenuItem.setToggleGroup(themeGroup);
        themeLightMenuItem.setToggleGroup(themeGroup);
        themeDarkMenuItem.setToggleGroup(themeGroup);
    }

    // Обработчики файлового меню
    @FXML
    protected void onOpenRom() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите ROM файл");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ROM файлы", "*.rom", "*.bin")
        );

        // Устанавливаем последний использованный путь
        AppSettings settings = AppSettings.getInstance();
        if (!settings.getLastRomPath().isEmpty()) {
            File lastDir = new File(settings.getLastRomPath()).getParentFile();
            if (lastDir != null && lastDir.exists()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        Stage stage = (Stage) menuBar.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null && emulator != null) {
            settings.saveLastRomPath(file.getAbsolutePath());
            // TODO: Реализовать загрузку ROM файла
            System.out.println("Загружается ROM: " + file.getAbsolutePath());
        }
    }

    @FXML
    protected void onOpenSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл снэпшота");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Снэпшоты", "*.z80", "*.sna", "*.szx")
        );

        // Устанавливаем последний использованный путь
        AppSettings settings = AppSettings.getInstance();
        if (!settings.getLastSnapshotPath().isEmpty()) {
            File lastDir = new File(settings.getLastSnapshotPath()).getParentFile();
            if (lastDir != null && lastDir.exists()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        Stage stage = (Stage) menuBar.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null && emulator != null) {
            settings.saveLastSnapshotPath(file.getAbsolutePath());
            // TODO: Реализовать загрузку снэпшота
            System.out.println("Загружается снэпшот: " + file.getAbsolutePath());
        }
    }

    @FXML
    protected void onSaveSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить снэпшот");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Z80 снэпшот", "*.z80")
        );

        Stage stage = (Stage) menuBar.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null && emulator != null) {
            // TODO: Реализовать сохранение снэпшота
            System.out.println("Сохраняется снэпшот: " + file.getAbsolutePath());
        }
    }

    @FXML
    protected void onExit() {
        if (emulator != null) {
            emulator.stop();
        }
        Platform.exit();
    }

    @FXML
    protected void onTapeLibrary() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tape-library-view.fxml"));
            BorderPane root = loader.load();

            TapeLibraryController controller = loader.getController();

            Stage libraryStage = new Stage();
            libraryStage.setTitle("Картотека кассет TAP/TZX");

            Scene libraryScene = new Scene(root, 800, 600);

            // Применяем текущую тему к новому окну
            ThemeManager.applyCurrentTheme(libraryScene);

            libraryStage.setScene(libraryScene);
            controller.setStage(libraryStage);

            // Устанавливаем иконку (если есть)
            if (scene != null && scene.getWindow() instanceof Stage) {
                Stage mainStage = (Stage) scene.getWindow();
                libraryStage.getIcons().addAll(mainStage.getIcons());
            }

            libraryStage.show();
            System.out.println("Открыто окно картотеки кассет");

        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Не удалось открыть картотеку кассет");
            alert.setContentText("Ошибка: " + e.getMessage());

            // Применяем тему к диалогу
            alert.getDialogPane().getScene().getStylesheets().clear();
            if (scene != null) {
                alert.getDialogPane().getScene().getStylesheets().addAll(scene.getStylesheets());
            }

            alert.showAndWait();
        }
    }

    // Обработчики меню эмуляции
    @FXML
    protected void onPause() {
        if (emulator != null) {
            if (isPaused) {
                //emulator.resume();
                pauseMenuItem.setText("Пауза");
                pauseButton.setText("Пауза");
                isPaused = false;
            } else {
                //emulator.pause();
                pauseMenuItem.setText("Продолжить");
                pauseButton.setText("Продолжить");
                isPaused = true;
            }
        }
    }

    @FXML
    protected void onReset() {
        if (emulator != null) {
            //emulator.reset();
            System.out.println("Эмулятор сброшен");
        }
    }

    @FXML
    protected void onFastLoad() {
        if (emulator != null) {
            // TODO: Реализовать быструю загрузку
            System.out.println("Быстрая загрузка включена");
        }
    }

    // Обработчики меню настроек
    @FXML
    protected void onZoom1() {
        if (emulator != null) {
            // TODO: Установить масштаб x1
            System.out.println("Масштаб установлен: x1");
        }
    }

    @FXML
    protected void onZoom2() {
        if (emulator != null) {
            // TODO: Установить масштаб x2
            System.out.println("Масштаб установлен: x2");
        }
    }

    @FXML
    protected void onZoom3() {
        if (emulator != null) {
            // TODO: Установить масштаб x3
            System.out.println("Масштаб установлен: x3");
        }
    }

    @FXML
    protected void onSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Настройки");
        alert.setHeaderText("Настройки эмулятора");
        alert.setContentText("Окно настроек будет реализовано позже.");

        // Применяем текущую тему к диалогу
        alert.getDialogPane().getScene().getStylesheets().clear();
        if (scene != null) {
            alert.getDialogPane().getScene().getStylesheets().addAll(scene.getStylesheets());
        }

        alert.showAndWait();
    }

    // Обработчики меню справки
    @FXML
    protected void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("О программе");
        alert.setHeaderText("ZX Spectrum Эмулятор");
        alert.setContentText("Эмулятор компьютера ZX Spectrum 48K\nВерсия 1.0\n\nРазработано с использованием JavaFX");

        // Применяем текущую тему к диалогу
        alert.getDialogPane().getScene().getStylesheets().clear();
        if (scene != null) {
            alert.getDialogPane().getScene().getStylesheets().addAll(scene.getStylesheets());
        }

        alert.showAndWait();
    }

    // Обработчики темы
    @FXML
    protected void onThemeSystem() {
        if (scene != null) {
            ThemeManager.applyTheme(scene, ThemeManager.Theme.SYSTEM);
            AppSettings.getInstance().saveTheme(ThemeManager.Theme.SYSTEM);
            System.out.println("Применена системная тема");
        }
    }

    @FXML
    protected void onThemeLight() {
        if (scene != null) {
            ThemeManager.applyTheme(scene, ThemeManager.Theme.LIGHT);
            AppSettings.getInstance().saveTheme(ThemeManager.Theme.LIGHT);
            System.out.println("Применена светлая тема");
        }
    }

    @FXML
    protected void onThemeDark() {
        if (scene != null) {
            ThemeManager.applyTheme(scene, ThemeManager.Theme.DARK);
            AppSettings.getInstance().saveTheme(ThemeManager.Theme.DARK);
            System.out.println("Применена темная тема");
        }
    }

}
