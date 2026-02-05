package spectrum.hardware.sound.ay;

import java.util.Arrays;

import static spectrum.hardware.sound.ay.AyConstants.*;

/**
 * Register file for AY-3-8912.
 * <p>
 * Provides type-safe access to all 16 registers with computed properties
 * for multi-byte values (tone periods, envelope period).
 *
 * @see <a href="http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf">AY-3-8910/8912 Datasheet</a>
 */
public class AyRegisterFile {

    private final int[] registers = new int[REGISTER_COUNT];

    /**
     * Reset all registers to initial state.
     */
    public void reset() {
        Arrays.fill(registers, 0);
        registers[AyRegister.MIXER.getIndex()] = MIXER_DEFAULT;
    }

    /**
     * Write value to register with masking.
     *
     * @param regIndex Register index (0-15)
     * @param value    Value to write
     */
    public void write(int regIndex, int value) {
        AyRegister reg = AyRegister.fromIndex(regIndex);
        if (reg != null) {
            registers[regIndex] = reg.masked(value);
        }
    }

    /**
     * Read raw register value.
     *
     * @param regIndex Register index (0-15)
     * @return Register value, or 0xFF for I/O ports
     */
    public int read(int regIndex) {
        if (regIndex >= IO_PORT_A_INDEX) {
            return 0xFF; // I/O ports return 0xFF when not connected
        }
        return registers[regIndex];
    }

    /**
     * Get 12-bit tone period for channel.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return Tone period (1-4095, 0 is treated as 1)
     */
    public int getTonePeriod(int channel) {
        int fine = registers[channel * 2];
        int coarse = registers[channel * 2 + 1] & TONE_COARSE_MASK;
        int period = fine | (coarse << 8);
        return period == 0 ? 1 : period; // Per datasheet: period 0 = period 1
    }

    /**
     * Get 5-bit noise period.
     *
     * @return Noise period (1-31, 0 is treated as 1)
     */
    public int getNoisePeriod() {
        int period = registers[AyRegister.NOISE_PERIOD.getIndex()] & NOISE_PERIOD_MASK;
        return period == 0 ? 1 : period;
    }

    /**
     * Get mixer register value.
     *
     * @return Raw mixer value
     */
    public int getMixer() {
        return registers[AyRegister.MIXER.getIndex()];
    }

    /**
     * Check if tone is enabled for channel.
     * <p>
     * Note: In mixer register, bit=0 means enabled, bit=1 means disabled.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return true if tone is enabled
     */
    public boolean isToneEnabled(int channel) {
        return (getMixer() & (1 << channel)) == 0;
    }

    /**
     * Check if noise is enabled for channel.
     * <p>
     * Note: In mixer register, bit=0 means enabled, bit=1 means disabled.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return true if noise is enabled
     */
    public boolean isNoiseEnabled(int channel) {
        return (getMixer() & (1 << (channel + 3))) == 0;
    }

    /**
     * Get raw amplitude register value for channel.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return Raw amplitude value (includes envelope mode bit)
     */
    public int getAmplitude(int channel) {
        return registers[AyRegister.AMPLITUDE_A.getIndex() + channel];
    }

    /**
     * Check if channel uses envelope mode.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return true if envelope mode is active
     */
    public boolean isEnvelopeMode(int channel) {
        return (getAmplitude(channel) & AMPLITUDE_ENVELOPE_MODE) != 0;
    }

    /**
     * Get 4-bit amplitude level for channel.
     *
     * @param channel Channel index (0=A, 1=B, 2=C)
     * @return Amplitude level (0-15)
     */
    public int getAmplitudeLevel(int channel) {
        return getAmplitude(channel) & AMPLITUDE_LEVEL_MASK;
    }

    /**
     * Get 16-bit envelope period.
     *
     * @return Envelope period (1-65535, 0 is treated as 1)
     */
    public int getEnvelopePeriod() {
        int fine = registers[AyRegister.ENVELOPE_FINE.getIndex()];
        int coarse = registers[AyRegister.ENVELOPE_COARSE.getIndex()];
        int period = fine | (coarse << 8);
        return period == 0 ? 1 : period;
    }

    /**
     * Get envelope shape code.
     *
     * @return Envelope shape (0-15)
     */
    public int getEnvelopeShape() {
        return registers[AyRegister.ENVELOPE_SHAPE.getIndex()] & ENVELOPE_SHAPE_MASK;
    }
}
