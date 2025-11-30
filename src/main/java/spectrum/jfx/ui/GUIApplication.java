package spectrum.jfx.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import spectrum.jfx.hardware.SpectrumEmulator;
import spectrum.jfx.ui.controller.MainController;
import spectrum.jfx.ui.localization.LocalizationManager;
import spectrum.jfx.ui.settings.AppSettings;
import spectrum.jfx.ui.theme.ThemeManager;

import java.io.IOException;
import java.util.logging.Logger;

import static spectrum.jfx.hardware.util.EmulatorUtils.loadFile;
import static spectrum.jfx.hardware.video.ZoomLevel.X2;

public class GUIApplication extends Application {

    private static final Logger logger = Logger.getLogger(GUIApplication.class.getName());

    @Override
    public void start(Stage stage) throws IOException {

        // Load saved settings
        AppSettings settings = AppSettings.getInstance();
        LocalizationManager localizationManager = LocalizationManager.getInstance();

        FXMLLoader fxmlLoader = new FXMLLoader(GUIApplication.class.getResource("/main-view.fxml"));
        // Set resource bundle for FXML loader
        fxmlLoader.setResources(java.util.ResourceBundle.getBundle("messages", localizationManager.getCurrentLanguage().getLocale()));
        BorderPane root = fxmlLoader.load();

        SpectrumEmulator emulator = new SpectrumEmulator();
        emulator.init();
        emulator.getVideo().setZoomLevel(X2);

        // Get controller and pass emulator reference to it
        MainController controller = fxmlLoader.getController();
        controller.setEmulator(emulator);

        // Calculate window size considering all UI elements
        // Canvas includes full size: screen + two borders on each side
        double videoWidth = (emulator.getVideo().getScaledScreenWidth() + 2 * emulator.getVideo().getScaledBorderSize());
        double videoHeight = (emulator.getVideo().getScaledScreenHeight() + 2 * emulator.getVideo().getScaledBorderSize());

        // Use saved window dimensions or calculated defaults
        double width = settings.isWindowMaximized() ? settings.getWindowWidth() : videoWidth + 40.0;
        double height = settings.isWindowMaximized() ? settings.getWindowHeight() : videoHeight + 40.0 + 100.0;

        // Debug output
        logger.info("Video canvas size: " + videoWidth + "x" + videoHeight);
        logger.info("Window size: " + width + "x" + height);

        Scene scene = new Scene(root, width, height);

        // Apply saved theme
        ThemeManager.applyTheme(scene, settings.getTheme());

        // Set corresponding theme radio button
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

        // Pass scene reference to controller for theme switching
        controller.setScene(scene);

        stage.setTitle(localizationManager.getString("app.title"));
        stage.setScene(scene);

        // Set minimum window size
        stage.setMinWidth(videoWidth + 40.0);
        stage.setMinHeight(videoHeight + 40.0 + 100.0);

        // Allow window resizing
        stage.setResizable(true);

        // Restore window state
        if (settings.isWindowMaximized()) {
            stage.setMaximized(true);
        }

        // Save window size when changed
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

        // Save maximization state
        stage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            settings.saveWindowSize(stage.getWidth(), stage.getHeight(), isMaximized);
        });

        stage.show();

        // Configure videoContainer for keyboard events
        controller.getVideoContainer().setFocusTraversable(true);
        controller.getVideoContainer().setOnKeyPressed(event -> {
            emulator.getKeyboard().keyPressed(event.getCode());
            event.consume(); // Prevent further event processing
        });
        controller.getVideoContainer().setOnKeyReleased(event -> {
            emulator.getKeyboard().keyReleased(event.getCode());
            event.consume(); // Prevent further event processing
        });

        // Return focus to videoContainer on mouse click
        controller.getVideoContainer().setOnMouseClicked(event -> controller.getVideoContainer().requestFocus());

        controller.getVideoContainer().getChildren().add((Node) emulator.getVideo().getCanvas());

        // Set focus on videoContainer after adding canvas
        controller.getVideoContainer().requestFocus();

        byte[] rom = loadFile("/roms/48.rom");
        emulator.getMemory().loadROM(rom);
        emulator.start();

    }

}
