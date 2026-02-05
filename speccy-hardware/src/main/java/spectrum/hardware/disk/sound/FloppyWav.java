package spectrum.hardware.disk.sound;

import lombok.SneakyThrows;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.Arrays;

public final class FloppyWav {

    @SneakyThrows
    public static short[] loadTrimmed(File file) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {

            AudioFormat f = ais.getFormat();

            if (f.getSampleSizeInBits() != 16 || f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                throw new IllegalArgumentException("Нужен 16-bit PCM WAV");
            }

            byte[] raw = ais.readAllBytes();
            int samples = raw.length / 2;
            short[] pcm = new short[samples];

            for (int i = 0; i < samples; i++) {
                pcm[i] = (short) (
                        (raw[i * 2 + 1] << 8) |
                                (raw[i * 2] & 0xff)
                );
            }

            return trimSilence(pcm, 400);
        }
    }

    private static short[] trimSilence(short[] in, int threshold) {
        int s = 0;
        int e = in.length - 1;

        while (s < in.length && Math.abs(in[s]) < threshold) s++;
        while (e > s && Math.abs(in[e]) < threshold) e--;

        return Arrays.copyOfRange(in, s, e + 1);
    }

}

