package spectrum.jfx.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import spectrum.jfx.ui.controller.MainController;
import spectrum.jfx.hardware.SpectrumEmulator;

import java.io.IOException;

import static spectrum.jfx.hardware.util.EmulatorUtils.loadFile;
import static spectrum.jfx.hardware.video.ZoomLevel.X2;

public class GUIApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(GUIApplication.class.getResource("/hello-view.fxml"));
        VBox root = fxmlLoader.load();

        SpectrumEmulator emulator = new SpectrumEmulator();
        emulator.init();
        emulator.getVideo().setZoomLevel(X2);

        double width = emulator.getVideo().getScaledScreenWidth() + 40.0 + emulator.getVideo().getScaledBorderSize();
        double height = emulator.getVideo().getScaledScreenHeight() + 40.0 + emulator.getVideo().getScaledBorderSize();

        Scene scene = new Scene(root, width, height);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();

        scene.setOnKeyPressed(event -> emulator.getKeyboard().keyPressed(event.getCode()));
        scene.setOnKeyReleased(event -> emulator.getKeyboard().keyReleased(event.getCode()));


        fxmlLoader.<MainController>getController().getVideoContainer().getChildren().add((Node) emulator.getVideo().getCanvas());

        byte[] rom = loadFile("/roms/48.rom");
        emulator.getMemory().loadROM(rom);
        emulator.start();

    }

}
