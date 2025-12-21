package spectrum.jfx.hardware.tape;

import spectrum.jfx.hardware.sound.Sound;
import spectrum.jfx.model.TapeFile;

public interface CassetteDeck {

    void setMotor(boolean on);

    void insertTape(TapeFile tape);

    void setSectionIndex(int index);

    void addCassetteDeckEventListener(CassetteDeckEvent listener);

    void setSound(Sound sound);

}
