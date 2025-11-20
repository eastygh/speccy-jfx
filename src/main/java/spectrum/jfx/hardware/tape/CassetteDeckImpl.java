package spectrum.jfx.hardware.tape;

import lombok.RequiredArgsConstructor;
import spectrum.jfx.hardware.ula.ClockListener;
import spectrum.jfx.hardware.ula.InPortListener;
import spectrum.jfx.hardware.ula.OutPortListener;
import spectrum.jfx.hardware.ula.Ula;

@RequiredArgsConstructor
public class CassetteDeckImpl implements InPortListener, OutPortListener,
        TapeSignal, CassetteDeck, ClockListener
{

    private final CassetteDeckEvent eventsReceiver;
    private final PilotToneSignal pilotToneSignal = new PilotToneSignal(2168, true);

    private volatile long tStates;

    /**
     *
     * @param port - 0xFE
     * @return D6 - 1 high, 0 low
     */
    @Override
    public byte inPort(int port) {
        boolean ear = pilotToneSignal.earLevelAt(tStates);
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

    @Override
    public void ticks(long tStates, int delta) {
        this.tStates = tStates;
    }
}
