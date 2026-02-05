package spectrum.jfx.driver.video;

import javafx.scene.paint.Color;
import lombok.experimental.UtilityClass;

import java.util.Arrays;

@UtilityClass
public class ColorsUtils {

    // Standard Spectrum colors
    static final Color[] SPECTRUM_COLORS = {
            Color.rgb(0, 0, 0),       // 0: Black
            Color.rgb(0, 0, 215),     // 1: Blue
            Color.rgb(215, 0, 0),     // 2: Red
            Color.rgb(215, 0, 215),   // 3: Magenta
            Color.rgb(0, 215, 0),     // 4: Green
            Color.rgb(0, 215, 215),   // 5: Cyan
            Color.rgb(215, 215, 0),   // 6: Yellow
            Color.rgb(215, 215, 215), // 7: White

            // Bright colors (с флагом BRIGHT)
            Color.rgb(0, 0, 0),       // 8: Bright Black
            Color.rgb(0, 0, 255),     // 9: Bright Blue
            Color.rgb(255, 0, 0),     // 10: Bright Red
            Color.rgb(255, 0, 255),   // 11: Bright Magenta
            Color.rgb(0, 255, 0),     // 12: Bright Green
            Color.rgb(0, 255, 255),   // 13: Bright Cyan
            Color.rgb(255, 255, 0),   // 14: Bright Yellow
            Color.rgb(255, 255, 255)  // 15: Bright White
    };

    static final int[] SPECTRUM_COLORS_ARGB = Arrays.stream(SPECTRUM_COLORS).mapToInt(ColorsUtils::toARGB).toArray();

    public static int toARGB(Color color) {
        // Convert double values (0.0-1.0) to int values (0-255)
        int alpha = (int) (color.getOpacity() * 255);
        int red = (int) (color.getRed() * 255);
        int green = (int) (color.getGreen() * 255);
        int blue = (int) (color.getBlue() * 255);

        // Combine into a 32-bit ARGB integer
        // Alpha is in the most significant byte, then Red, Green, and Blue
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

}
