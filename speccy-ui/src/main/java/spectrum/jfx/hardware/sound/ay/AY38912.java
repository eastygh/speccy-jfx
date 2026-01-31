package spectrum.jfx.hardware.sound.ay;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.machine.Device;
import spectrum.hardware.machine.MachineSettings;
import spectrum.hardware.sound.Sound;
import spectrum.hardware.ula.InPortListener;
import spectrum.hardware.ula.OutPortListener;

import static spectrum.jfx.hardware.sound.ay.AyConstants.*;

/**
 * AY-3-8912 Programmable Sound Generator emulation.
 * <p>
 * This class implements the Device interfaces and coordinates between
 * the chip core emulation and audio output.
 * <p>
 * The AY-3-8912 is a 3-voice programmable sound generator designed by
 * General Instrument in 1978. It features:
 * <ul>
 *     <li>3 square wave tone generators (12-bit period)</li>
 *     <li>1 noise generator (17-bit LFSR)</li>
 *     <li>1 envelope generator (16 shapes)</li>
 *     <li>Logarithmic DAC (4-bit, 16 levels)</li>
 * </ul>
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
@Slf4j
public class AY38912 implements InPortListener, OutPortListener, Sound, Device {

    @Getter
    private final AyChipCore chipCore;
    private final AyAudioOutput audioOutput;

    private final double tStatesPerSample;
    private double tStateAcc;

    private volatile boolean speedUpMode = false;

    /**
     * Create AY-3-8912 emulator.
     *
     * @param settings Machine settings (provides clock frequency)
     */
    public AY38912(MachineSettings settings) {
        double cpuFreq = settings.getMachineType().clockFreq;
        double ayClock = cpuFreq / 2.0; // AY clock is half CPU clock

        this.tStatesPerSample = cpuFreq / DEFAULT_SAMPLE_RATE;
        this.chipCore = new AyChipCore(ayClock, DEFAULT_SAMPLE_RATE);
        this.audioOutput = new AyAudioOutput(DEFAULT_SAMPLE_RATE);

        audioOutput.open();
    }

    // ========== Device Interface ==========

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        chipCore.reset();
        audioOutput.reset();
        tStateAcc = 0;
    }

    @Override
    public void open() {
        // Audio already opened in constructor
    }

    @Override
    public void close() {
        audioOutput.close();
    }

    @Override
    public void setSpeedUpMode(boolean speedUpMode) {
        this.speedUpMode = speedUpMode;
    }

    // ========== Port I/O ==========

    /**
     * Read from AY port.
     * <p>
     * Port 0xFFFD (masked 0xC000): Read data from selected register.
     *
     * @param port Port address
     * @return Register value or 0xFF if not AY port
     */
    @Override
    public int inPort(int port) {
        if ((port & PORT_MASK) == PORT_REGISTER_SELECT) {
            return chipCore.readData();
        }
        return 0xFF;
    }

    /**
     * Write to AY port.
     * <p>
     * Port 0xFFFD (masked 0xC000): Select register.
     * Port 0xBFFD (masked 0x8000): Write data to selected register.
     *
     * @param port  Port address
     * @param value Value to write
     */
    @Override
    public void outPort(int port, int value) {
        int maskedPort = port & PORT_MASK;
        value &= 0xFF;

        if (maskedPort == PORT_REGISTER_SELECT) {
            chipCore.selectRegister(value);
        } else if (maskedPort == PORT_DATA_WRITE) {
            chipCore.writeData(value);
        }
    }

    // ========== Clock Listener ==========

    /**
     * Process CPU clock ticks and generate audio samples.
     *
     * @param tStates Current T-state count
     * @param delta   T-states since last call
     */
    @Override
    public void ticks(long tStates, int delta) {
        if (speedUpMode || !audioOutput.isEnabled()) {
            return;
        }

        tStateAcc += delta;
        while (tStateAcc >= tStatesPerSample) {
            tStateAcc -= tStatesPerSample;
            renderSample();
        }
    }

    /**
     * Render one audio sample and write to output.
     */
    private void renderSample() {
        double[] sample = chipCore.generateSample();
        audioOutput.writeSample(sample[0], sample[1]);
    }

    // ========== Sound Interface ==========

    @Override
    public void endFrame() {
        audioOutput.flush();
    }

    @Override
    public double getVolume() {
        return audioOutput.getVolume();
    }

    @Override
    public void setVolume(double volume) {
        audioOutput.setVolume(volume);
    }

    @Override
    public void mute(boolean state) {
        audioOutput.setMuted(state);
    }

    @Override
    public void play(int cycles) {
        // Not used for AY chip - audio is generated via ticks()
    }

    @Override
    public void pushBackTape(boolean state) {
        // Not used for AY chip - tape input goes to beeper
    }

    // ========== Configuration ==========

    /**
     * Set stereo panning mode.
     *
     * @param mode Panning mode (MONO, ABC, ABC_WIDE, etc.)
     */
    public void setPanningMode(PanningMode mode) {
        chipCore.setPanningMode(mode);
    }

    /**
     * Get current panning mode.
     *
     * @return Current panning mode
     */
    public PanningMode getPanningMode() {
        return chipCore.getPanningMode();
    }

}
