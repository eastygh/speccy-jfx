package spectrum.hardware.sound;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;

@Slf4j
@UtilityClass
public class SoundUtils {

    static SourceDataLine initializeAudio(AudioFormat format, int bufferSize) {
        SourceDataLine audioLine;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                log.warn("Audio line not supported, sound will be disabled");
                return null;
            }

            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format, bufferSize);
            audioLine.start();

            log.info("Audio line initialized: {} Hz, {} bit, {} channel",
                    format.getSampleRate(), format.getSampleSizeInBits(),
                    format.getChannels());

        } catch (LineUnavailableException e) {
            log.warn("Audio line unavailable, sound will be disabled: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to initialize audio, sound will be disabled: {}", e.getMessage());
            return null;
        }
        return audioLine;
    }

}
