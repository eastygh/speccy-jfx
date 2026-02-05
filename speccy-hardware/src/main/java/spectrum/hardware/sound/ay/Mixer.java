package spectrum.hardware.sound.ay;

import static spectrum.hardware.sound.ay.AyConstants.AY_GAIN;
import static spectrum.hardware.sound.ay.AyConstants.CHANNEL_COUNT;

/**
 * Mixer for AY-3-8912 channels.
 * <p>
 * Combines tone and noise outputs with amplitude/envelope to produce
 * final channel samples, then applies stereo panning.
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class Mixer {

    private PanningMode panningMode = PanningMode.ABC_WIDE;

    /**
     * Mix all channels and produce stereo sample.
     *
     * @param registers   Register file for amplitude and mixer settings
     * @param toneOutputs Tone generator outputs [A, B, C]
     * @param noiseOutput Noise generator output
     * @param envLevel    Envelope generator level (0-15)
     * @return Stereo sample as [left, right]
     */
    public double[] mix(AyRegisterFile registers,
                        boolean[] toneOutputs,
                        boolean noiseOutput,
                        int envLevel) {

        double left = 0;
        double right = 0;

        for (int ch = 0; ch < CHANNEL_COUNT; ch++) {
            double amplitude = computeChannelAmplitude(
                    registers, ch, toneOutputs[ch], noiseOutput, envLevel);

            left += amplitude * panningMode.getLeftPan(ch);
            right += amplitude * panningMode.getRightPan(ch);
        }

        return new double[]{left, right};
    }

    /**
     * Compute amplitude for a single channel.
     * <p>
     * Per datasheet: The output is HIGH when the corresponding generator is disabled.
     * Both tone and noise gates must be HIGH for output.
     */
    private double computeChannelAmplitude(AyRegisterFile registers,
                                           int channel,
                                           boolean toneOutput,
                                           boolean noiseOutput,
                                           int envLevel) {

        boolean toneEnabled = registers.isToneEnabled(channel);
        boolean noiseEnabled = registers.isNoiseEnabled(channel);

        // Per datasheet: output is HIGH when generator is disabled
        boolean toneGate = !toneEnabled || toneOutput;
        boolean noiseGate = !noiseEnabled || noiseOutput;

        // Both gates must be HIGH for output
        if (!toneGate || !noiseGate) {
            return 0;
        }

        // Get volume level
        int volumeLevel;
        if (registers.isEnvelopeMode(channel)) {
            volumeLevel = envLevel;
        } else {
            volumeLevel = registers.getAmplitudeLevel(channel);
        }

        return VolumeTable.getAmplitude(volumeLevel) * AY_GAIN;
    }

    /**
     * Set stereo panning mode.
     *
     * @param mode Panning mode
     */
    public void setPanningMode(PanningMode mode) {
        this.panningMode = mode;
    }

    /**
     * Get current panning mode.
     *
     * @return Current panning mode
     */
    public PanningMode getPanningMode() {
        return panningMode;
    }
}
