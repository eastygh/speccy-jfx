package spectrum.jfx.hardware.tape;

import lombok.RequiredArgsConstructor;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.hardware.ula.Ula;

@RequiredArgsConstructor
public class CassetteDeck implements InPortListener, OutPortListener, TapeSignal {

    private final Ula ula;

    private final PilotToneSignal pilotToneSignal = new PilotToneSignal(2168, true);

    /**
     *
     * @param port - 0xFE
     * @return D6 - 1 high, 0 low
     */
    @Override
    public byte inPort(int port) {
        boolean ear = pilotToneSignal.earLevelAt(ula.gettStates());
        int value = ear ? 0b0000_0000 : 0b0100_0000;
        return (byte) (value & 0xff);
    }

    @Override
    public void outPort(int port, int value) {

    }

    @Override
    public boolean earLevelAt(long tstates) {
        return false;
    }

}
