package spectrum.jfx.hardware.tape;

import spectrum.jfx.hardware.machine.Device;
import spectrum.jfx.hardware.sound.Sound;
import spectrum.jfx.model.TapeFile;

public interface CassetteDeck extends Device {

    void setMotor(boolean on);

    void insertTape(TapeFile tape);

    void setSectionIndex(int index);

    void addCassetteDeckEventListener(CassetteDeckEvent listener);

    void setSound(Sound sound);

    void setSoundPushBack(boolean pushBack);

}
