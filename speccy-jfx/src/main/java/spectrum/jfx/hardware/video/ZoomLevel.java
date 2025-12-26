package spectrum.jfx.hardware.video;

import lombok.Getter;

@Getter
public enum ZoomLevel {
    X1(1, "1x"),
    X2(2, "2x"),
    X4(4, "4x");

    private final int scale;
    private final String displayName;

    ZoomLevel(int scale, String displayName) {
        this.scale = scale;
        this.displayName = displayName;
    }

}
