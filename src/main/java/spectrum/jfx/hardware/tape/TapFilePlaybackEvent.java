package spectrum.jfx.hardware.tape;

import spectrum.jfx.model.TapeFile;

public interface TapFilePlaybackEvent {

    void onSectionChanged(int index, TapeFile tape);

}
