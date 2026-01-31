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
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.logging;

    requires org.lwjgl.glfw;
    requires org.apache.commons.lang3;
    requires z80core;
    requires z80processor;
    requires speccy.hardware;

    opens spectrum.jfx to javafx.fxml;
    exports spectrum.jfx;

    exports spectrum.jfx.ui;
    opens spectrum.jfx.ui to javafx.fxml;

    exports spectrum.jfx.ui.controller;
    opens spectrum.jfx.ui.controller to javafx.fxml;

    exports spectrum.jfx.ui.settings;
    opens spectrum.jfx.ui.settings to com.fasterxml.jackson.databind;

    exports spectrum.jfx.ui.theme;
    opens spectrum.jfx.ui.theme to com.fasterxml.jackson.databind;

    exports spectrum.jfx.ui.model;
    opens spectrum.jfx.ui.model to com.fasterxml.jackson.databind;

    exports spectrum.jfx.hardware.sound;

}