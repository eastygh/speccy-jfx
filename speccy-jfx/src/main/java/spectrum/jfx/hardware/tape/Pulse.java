package spectrum.jfx.hardware.tape;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Pulse {

    private int duration;
    private boolean level;

}
