module spectrum.jfx {

    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.slf4j;
    requires java.desktop;
    requires static lombok;
    requires javafx.graphics;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;

    opens spectrum.jfx to javafx.fxml;
    exports spectrum.jfx;
    exports spectrum.jfx.ui;
    opens spectrum.jfx.ui to javafx.fxml;
    exports spectrum.jfx.ui.controller;
    opens spectrum.jfx.ui.controller to javafx.fxml;

}