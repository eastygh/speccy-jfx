package spectrum.jfx.ui.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.hardware.cpu.CPU;
import spectrum.jfx.hardware.disk.DiskController;
import spectrum.jfx.hardware.disk.TRDOSController;
import spectrum.jfx.hardware.machine.Emulator;
import spectrum.jfx.hardware.machine.HardwareProvider;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.machine.Machine;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.ui.localization.LocalizationManager.LocalizationChangeListener;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.theme.ThemeManager;

import java.io.File;
import java.io.IOException;

@Slf4j
@Getter
@Setter
public class MainController implements LocalizationChangeListener {

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
    private RadioMenuItem languageEnglishMenuItem;
    @FXML
    private RadioMenuItem languageRussianMenuItem;
    @FXML
    private MenuItem settingsMenuItem;
    @FXML
    private MenuItem aboutMenuItem;

    // Toolbar buttons
    @FXML
    private Button tapeLibraryButton;
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

    private Scene scene;
    private boolean isPaused = false;
    private LocalizationManager localizationManager;

    public void initialize() {
        // Получаем экземпляр LocalizationManager и регистрируем слушателя
        localizationManager = LocalizationManager.getInstance();
        localizationManager.addLanguageChangeListener(this);

        // Отключаем перехват клавиш для кнопок панели инструментов
        disableKeyboardNavigationForButtons();

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

        // Группировка радиокнопок языка
        ToggleGroup languageGroup = new ToggleGroup();
        languageEnglishMenuItem.setToggleGroup(languageGroup);
        languageRussianMenuItem.setToggleGroup(languageGroup);

        // Устанавливаем текущий язык
        updateLanguageSelection();
    }

    // Обработчики файлового меню
    @FXML
    protected void onOpenRom() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationManager.getString("filechooser.rom.title", "Select ROM file"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.rom", "ROM files"), "*.rom", "*.bin")
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

        if (file != null && getEmulator() != null) {
            settings.saveLastRomPath(file.getAbsolutePath());
            Machine.withHardwareProvider(hp -> hp.getEmulator().loadRom(file.getAbsolutePath()));
            log.info(localizationManager.getString("tape.loadingRom", "Loading ROM: {0}", file.getName()));
        }
    }

    @FXML
    protected void onOpenSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationManager.getString("filechooser.snapshot.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(localizationManager.getString("filechooser.snapshot"), "*.z80", "*.sna", "*.szx")
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

        if (file != null && getEmulator() != null) {
            settings.saveLastSnapshotPath(file.getAbsolutePath());
            // TODO: do snapshot loading
            log.info(localizationManager.getString("tape.loadingSnapshot", file.getName()));
        }
    }

    @FXML
    protected void onSaveSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationManager.getString("filechooser.snapshot.save"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Z80 snapshot", "*.z80")
        );

        Stage stage = (Stage) menuBar.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null && getEmulator() != null) {
            // TODO: Реализовать сохранение снэпшота
            log.info(localizationManager.getString("tape.savingSnapshot", file.getName()));
        }
    }

    @FXML
    protected void onExit() {
        confirmAndExit();
    }

    public void confirmAndExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(localizationManager.getString("confirm.exit.title"));
        alert.setHeaderText(localizationManager.getString("confirm.exit.header"));
        alert.setContentText(localizationManager.getString("confirm.exit.content"));

        // Применяем тему к диалогу
        alert.getDialogPane().getScene().getStylesheets().clear();
        if (scene != null) {
            alert.getDialogPane().getScene().getStylesheets().addAll(scene.getStylesheets());
        }

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (getEmulator() != null) {
                getEmulator().stop();
            }
            Platform.exit();
        }
    }

    @FXML
    protected void onTapeLibrary() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/controller/tape-library-view.fxml"));
            // Set the resource bundle for the FXML loader
            loader.setResources(java.util.ResourceBundle.getBundle("i18n/messages", localizationManager.getCurrentLanguage().getLocale()));
            BorderPane root = loader.load();

            TapeLibraryController controller = loader.getController();

            Stage libraryStage = new Stage();
            libraryStage.setTitle(localizationManager.getString("tape.title"));

            Scene libraryScene = new Scene(root, 800, 600);

            // Применяем текущую тему к новому окну
            ThemeManager.applyCurrentTheme(libraryScene);

            libraryStage.setScene(libraryScene);
            controller.setStage(libraryStage);

            // Устанавливаем иконку (если есть)
            if (scene != null && scene.getWindow() instanceof Stage mainStage) {
                libraryStage.getIcons().addAll(mainStage.getIcons());
            }

            libraryStage.show();
            log.info(localizationManager.getString("tape.tapeLibraryOpened"));

        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(localizationManager.getString("error.title"));
            alert.setHeaderText(localizationManager.getString("error.tapeLibraryFailed"));
            alert.setContentText(localizationManager.getString("error.message", e.getMessage()));

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
        if (getEmulator() != null) {
            if (isPaused) {
                getEmulator().resume();
                pauseMenuItem.setText(localizationManager.getString("menu.emulation.pause"));
                pauseButton.setText(localizationManager.getString("btn.pause"));
                isPaused = false;
            } else {
                getEmulator().pause();
                pauseMenuItem.setText(localizationManager.getString("menu.emulation.resume"));
                pauseButton.setText(localizationManager.getString("btn.resume"));
                isPaused = true;
            }
        }
    }

    @FXML
    protected void onReset() {
        if (getEmulator() != null) {
            getEmulator().reset();
            log.info(localizationManager.getString("tape.resetEmulator"));
        }
    }

    @FXML
    protected void onFastLoad() {
        if (getEmulator() != null) {
            // TODO: Реализовать быструю загрузку
            log.info("Быстрая загрузка включена");
        }
    }

    private Stage debugStage;

    @FXML
    protected void onDebug() {
        if (getEmulator() == null) return;

        if (debugStage != null && debugStage.isShowing()) {
            debugStage.toFront();
            return;
        }

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/controller/debug-view.fxml"));
            fxmlLoader.setResources(java.util.ResourceBundle.getBundle("i18n/messages", localizationManager.getCurrentLanguage().getLocale()));

            BorderPane root = fxmlLoader.load();
            DebugController controller = fxmlLoader.getController();
            if (getEmulator() instanceof spectrum.jfx.hardware.machine.HardwareProvider hp) {
                controller.setHardwareProvider(hp);
            }

            debugStage = new Stage();
            debugStage.setTitle(localizationManager.getString("debug.title"));
            Scene debugScene = new Scene(root);

            // Применяем текущую тему к окну отладки
            ThemeManager.applyTheme(debugScene, AppSettings.getInstance().getTheme());

            debugStage.setScene(debugScene);
            debugStage.setOnCloseRequest(event -> {
                controller.onClose();
            });
            debugStage.show();
        } catch (IOException e) {
            log.error("Failed to open debug window", e);
        }
    }

    // Обработчики меню настроек
    @FXML
    protected void onZoom1() {
        if (getEmulator() != null) {
            // TODO: Установить масштаб x1
            log.info("Масштаб установлен: x1");
        }
    }

    @FXML
    protected void onZoom2() {
        if (getEmulator() != null) {
            // TODO: Установить масштаб x2
            log.info("Масштаб установлен: x2");
        }
    }

    @FXML
    protected void onZoom3() {
        if (getEmulator() != null) {
            // TODO: Установить масштаб x3
            log.info("Масштаб установлен: x3");
        }
    }

    @FXML
    protected void onSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(localizationManager.getString("settings.title"));
        alert.setHeaderText(localizationManager.getString("settings.header"));
        alert.setContentText(localizationManager.getString("settings.content"));

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
        alert.setTitle(localizationManager.getString("about.title"));
        alert.setHeaderText(localizationManager.getString("about.header"));
        alert.setContentText(localizationManager.getString("about.content"));

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
            log.info("Применена системная тема");
        }
    }

    @FXML
    protected void onThemeLight() {
        if (scene != null) {
            ThemeManager.applyTheme(scene, ThemeManager.Theme.LIGHT);
            AppSettings.getInstance().saveTheme(ThemeManager.Theme.LIGHT);
            log.info("Применена светлая тема");
        }
    }

    @FXML
    protected void onThemeDark() {
        if (scene != null) {
            ThemeManager.applyTheme(scene, ThemeManager.Theme.DARK);
            AppSettings.getInstance().saveTheme(ThemeManager.Theme.DARK);
            log.info("Применена темная тема");
        }
    }

    // Обработчики переключения языка
    @FXML
    protected void onLanguageEnglish() {
        localizationManager.setLanguage(LocalizationManager.Language.ENGLISH);
    }

    @FXML
    protected void onLanguageRussian() {
        localizationManager.setLanguage(LocalizationManager.Language.RUSSIAN);
    }

    // Реализация LocalizationChangeListener
    @Override
    public void onLanguageChanged(LocalizationManager.Language newLanguage) {
        Platform.runLater(this::updateLanguageSelection);
        updateDynamicTexts();
    }

    /**
     * Отключает перехват клавиш (особенно пробела) кнопками панели инструментов
     */
    private void disableKeyboardNavigationForButtons() {
        // Отключаем фокус по табуляции для кнопок
        openRomButton.setFocusTraversable(false);
        pauseButton.setFocusTraversable(false);
        resetButton.setFocusTraversable(false);
        snapshotButton.setFocusTraversable(false);
        saveButton.setFocusTraversable(false);
        settingsButton.setFocusTraversable(false);
    }

    private void updateLanguageSelection() {
        LocalizationManager.Language currentLanguage = localizationManager.getCurrentLanguage();
        switch (currentLanguage) {
            case ENGLISH:
                languageEnglishMenuItem.setSelected(true);
                break;
            case RUSSIAN:
                languageRussianMenuItem.setSelected(true);
                break;
        }
    }

    private void updateDynamicTexts() {
        // Обновляем тексты, которые могут меняться динамически (например, кнопка паузы)
        if (isPaused) {
            pauseMenuItem.setText(localizationManager.getString("menu.emulation.resume"));
            pauseButton.setText(localizationManager.getString("btn.resume"));
        } else {
            pauseMenuItem.setText(localizationManager.getString("menu.emulation.pause"));
            pauseButton.setText(localizationManager.getString("btn.pause"));
        }
    }

    public void onTapeLibraryOpen(ActionEvent actionEvent) {
        onTapeLibrary();
    }

    public Emulator getEmulator() {
        return Machine.getHardwareProvider().getEmulator();
    }

    public void onTrDos(ActionEvent actionEvent) {
        HardwareProvider hardwareProvider = Machine.getHardwareProvider();
        final Emulator emulator = getEmulator();
        Memory memory = hardwareProvider.getMemory();
        DiskController diskController = emulator.getDiskController();
        if (diskController == null || !emulator.getMachineSettings().isEnableTRDOS()) {
            log.error("TR-DOS not enabled or no disk controller");
            return;
        }
        TRDOSController trdosController = diskController.getTrdosController();
        if (trdosController == null) {
            log.error("No TRDOSController present in scope");
            return;
        }
        emulator.pause();
        if (!emulator.waitForHold()) {
            log.error("Can't pause emulator");
        }
        memory.outPort(0x7FFD, 16);
        //trdosController.switchToTRDOS();
        //hardwareProvider.getCPU().setRegPC(15616);
        hardwareProvider.getCPU().setRegPC(0);
        emulator.addBreakPointListener(4780, (addr, op) -> {
            CPU cpu = hardwareProvider.getCPU();
            int sp = cpu.getRegSP();
            sp -= 2;
            hardwareProvider.getMemory().writeWord(sp, 4780);
            cpu.setRegSP(sp);
            emulator.removeBreakPointListener(4780);
            cpu.setRegPC(15616);
            Platform.runLater(() -> videoContainer.requestFocus());
            return 0;
        });
        emulator.resume();
    }

}
