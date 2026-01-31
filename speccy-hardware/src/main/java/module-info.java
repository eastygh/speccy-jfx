module speccy.hardware {
    exports spectrum.hardware.machine;
    exports spectrum.hardware.memory;
    exports spectrum.hardware.tape.model;
    exports spectrum.hardware.util;
    exports spectrum.hardware.debug;
    exports spectrum.hardware.cpu;
    exports spectrum.hardware.disk;
    exports spectrum.hardware.ula;
    exports spectrum.hardware.input;
    exports spectrum.hardware.sound;
    exports spectrum.hardware.video;
    exports spectrum.hardware.tape;
    exports spectrum.hardware.tape.events;
    exports spectrum.hardware.tape.playback;
    exports spectrum.hardware.tape.record;
    exports spectrum.hardware.tape.tap;
    exports spectrum.hardware.snapshot;
    exports spectrum.hardware.tape.flash;
    requires static lombok;
    requires org.slf4j;
    requires z80core;
    requires org.apache.commons.lang3;
    requires z80processor;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    opens spectrum.hardware.tape.model to com.fasterxml.jackson.databind;
    exports spectrum.hardware.disk.wd1793;
    exports spectrum.hardware.disk.wd1793.sound;
    exports spectrum.hardware.factory;
}