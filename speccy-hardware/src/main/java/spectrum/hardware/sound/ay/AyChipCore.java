package spectrum.hardware.sound.ay;

import static spectrum.hardware.sound.ay.AyConstants.*;

/**
 * Core emulation of AY-3-8912 Programmable Sound Generator.
 * <p>
 * This class handles chip-level emulation without audio output concerns.
 * It orchestrates the register file, tone generators, noise generator,
 * envelope generator, and mixer.
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class AyChipCore {

    private final AyRegisterFile registers = new AyRegisterFile();
    private final ToneGenerator[] toneGenerators = new ToneGenerator[CHANNEL_COUNT];
    private final NoiseGenerator noiseGenerator = new NoiseGenerator();
    private final EnvelopeGenerator envelopeGenerator = new EnvelopeGenerator();
    private final Mixer mixer = new Mixer();

    private int selectedRegister;
    private final double baseStep;
    private final double envBaseStep;

    /**
     * Create AY chip core.
     *
     * @param ayClock    AY clock frequency (typically CPU clock / 2)
     * @param sampleRate Audio sample rate (e.g., 44100)
     */
    public AyChipCore(double ayClock, int sampleRate) {
        this.baseStep = ayClock / (TONE_DIVIDER * sampleRate);
        this.envBaseStep = ayClock / (ENVELOPE_DIVIDER * sampleRate);

        for (int i = 0; i < CHANNEL_COUNT; i++) {
            toneGenerators[i] = new ToneGenerator();
        }
    }

    /**
     * Reset chip to initial state.
     */
    public void reset() {
        registers.reset();
        for (ToneGenerator tg : toneGenerators) {
            tg.reset();
        }
        noiseGenerator.reset();
        envelopeGenerator.reset();
        selectedRegister = 0;
    }

    /**
     * Select register for subsequent read/write.
     *
     * @param regIndex Register index (0-15)
     */
    public void selectRegister(int regIndex) {
        this.selectedRegister = regIndex & 0x0F;
    }

    /**
     * Write to currently selected register.
     *
     * @param value Value to write
     */
    public void writeData(int value) {
        registers.write(selectedRegister, value);

        // Envelope shape write triggers generator reset
        if (selectedRegister == AyRegister.ENVELOPE_SHAPE.getIndex()) {
            envelopeGenerator.setShape(value & ENVELOPE_SHAPE_MASK);
        }
    }

    /**
     * Read from currently selected register.
     *
     * @return Register value
     */
    public int readData() {
        if (selectedRegister >= IO_PORT_A_INDEX) {
            return 0xFF; // I/O ports return 0xFF
        }
        return registers.read(selectedRegister);
    }

    /**
     * Generate one audio sample.
     *
     * @return Stereo sample as [left, right]
     */
    public double[] generateSample() {
        // Update generators
        boolean[] toneOutputs = updateToneGenerators();
        boolean noiseOutput = updateNoiseGenerator();
        int envLevel = updateEnvelopeGenerator();

        // Mix and return
        return mixer.mix(registers, toneOutputs, noiseOutput, envLevel);
    }

    /**
     * Update all three tone generators.
     *
     * @return Array of tone outputs [A, B, C]
     */
    private boolean[] updateToneGenerators() {
        boolean[] outputs = new boolean[CHANNEL_COUNT];
        for (int ch = 0; ch < CHANNEL_COUNT; ch++) {
            int period = registers.getTonePeriod(ch);
            double step = baseStep / period;
            outputs[ch] = toneGenerators[ch].update(step);
        }
        return outputs;
    }

    /**
     * Update noise generator.
     *
     * @return Noise output state
     */
    private boolean updateNoiseGenerator() {
        int period = registers.getNoisePeriod();
        double step = baseStep / period;
        return noiseGenerator.update(step);
    }

    /**
     * Update envelope generator.
     *
     * @return Envelope level (0-15)
     */
    private int updateEnvelopeGenerator() {
        int period = registers.getEnvelopePeriod();
        double step = envBaseStep / period;
        return envelopeGenerator.update(step);
    }

    /**
     * Set stereo panning mode.
     *
     * @param mode Panning mode
     */
    public void setPanningMode(PanningMode mode) {
        mixer.setPanningMode(mode);
    }

    /**
     * Get current panning mode.
     *
     * @return Current panning mode
     */
    public PanningMode getPanningMode() {
        return mixer.getPanningMode();
    }

    /**
     * Get register file for inspection.
     *
     * @return Register file
     */
    public AyRegisterFile getRegisters() {
        return registers;
    }

    /**
     * Get currently selected register index.
     *
     * @return Selected register index (0-15)
     */
    public int getSelectedRegister() {
        return selectedRegister;
    }
}
