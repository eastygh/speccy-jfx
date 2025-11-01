package spectrum.jfx.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import spectrum.jfx.ui.controller.MainController;
import spectrum.jfx.z80.SpectrumEmulator;

import java.io.IOException;

import static spectrum.jfx.z80.util.EmulatorUtils.loadFile;
import static spectrum.jfx.z80.video.ZoomLevel.X2;

public class GUIApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(GUIApplication.class.getResource("/hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1024, 1024);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();

        SpectrumEmulator emulator = new SpectrumEmulator();
        emulator.init();
        emulator.getVideo().setZoomLevel(X2);

        fxmlLoader.<MainController>getController().getVideoContainer().getChildren().add((Node) emulator.getVideo().getCanvas());

        byte[] rom = loadFile("/roms/48.rom");
        emulator.getMemory().loadROM(rom);
        emulator.start();
    }

}
