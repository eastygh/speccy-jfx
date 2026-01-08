package spectrum.jfx.hardware.memory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RomAnalyzeTest {

    @Test
    void analyze48Rom() throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get("src/main/resources/roms/48.rom"));
        int address = 0x33EA;
        System.out.printf("ROM[0x%04X] = 0x%02X\n", address, rom[address] & 0xFF);
        System.out.printf("ROM[0x%04X] = 0x%02X\n", address + 1, rom[address + 1] & 0xFF);

        // Посмотрим окрестность
        for (int i = address - 5; i <= address + 5; i++) {
            System.out.printf("0x%04X: 0x%02X\n", i, rom[i] & 0xFF);
        }
    }
}
