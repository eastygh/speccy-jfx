package spectrum.jfx.hardware.tape;

import lombok.RequiredArgsConstructor;
import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.model.TapeFile;

@RequiredArgsConstructor
public class FastTapLoader {

    private final TapeFile tapeFile;
    private final Memory memory;

    public void load() {
        if (tapeFile == null || memory == null) {
            return;
        }
    }

}
