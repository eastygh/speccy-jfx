package spectrum.jfx.hardware.tape;

import spectrum.jfx.model.TapeFile;

public interface CassetteDeckEvent {

    void onTapeChanged(TapeFile tape);

    void onTapeSectionChanged(int sectionIndex, TapeFile file);

    void onTapePositionChanged(long position);

    void onTapeMotorChanged(boolean on);

    void onTapeFinished(boolean success);

    void onTapeError(String message);

}
