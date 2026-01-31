package spectrum.jfx.hardware.disk;

import lombok.extern.slf4j.Slf4j;
import spectrum.hardware.disk.wd1793.sound.LoopPlayer;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;

@Slf4j
public final class WavLoopPlayer implements LoopPlayer, Runnable {

    private final URL[] wavUrls;

    private volatile boolean running = true;
    private volatile boolean playing = false;

    private int currentIndex = -1;

    private AudioInputStream stream;
    private SourceDataLine line;

    private final Object lock = new Object();

    public WavLoopPlayer(URL... wavUrls) {
        this.wavUrls = wavUrls;
        Thread thread = new Thread(this, "wav-loop-player");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void play(int index) {
        if (index < 0 || index >= wavUrls.length) {
            log.warn("Bad wav index: {}", index);
            return;
        }
        if (playing && currentIndex == index) {
            return; // Already playing this track
        }

        synchronized (lock) {
            if (currentIndex != index) {
                closeLine();
                currentIndex = index;
                openLine(wavUrls[index]);
            }

            playing = true;
            lock.notifyAll();
        }
    }

    @Override
    public void pause() {
        playing = false;
    }

    @Override
    public void stop() {
        // Just stop playback, don't terminate the thread
        synchronized (lock) {
            playing = false;
            closeLine();
            currentIndex = -1;
        }
    }

    /**
     * Shuts down the player completely.
     * After this call, the player cannot be used anymore.
     */
    public void shutdown() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        closeLine();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];

        while (running) {
            synchronized (lock) {
                while (!playing && running) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            if (!running || line == null || stream == null) {
                continue;
            }

            try {
                int read = stream.read(buffer, 0, buffer.length);
                if (read == -1) {
                    // Loop back to the beginning
                    reopenStream();
                    continue;
                }
                if (playing) {
                    line.write(buffer, 0, read);
                }
            } catch (Exception e) {
                log.error("Error playing wav", e);
                pause();
            }
        }
    }

    private void openLine(URL wavUrl) {
        try {
            InputStream is = wavUrl.openStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            stream = AudioSystem.getAudioInputStream(bis);
            AudioFormat format = stream.getFormat();

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            log.debug("Opened audio line for: {}", wavUrl);
        } catch (Exception e) {
            log.error("Cannot open wav: {}", wavUrl, e);
            line = null;
            stream = null;
        }
    }

    private void reopenStream() {
        if (currentIndex < 0 || currentIndex >= wavUrls.length) {
            return;
        }
        try {
            if (stream != null) {
                stream.close();
            }
            InputStream is = wavUrls[currentIndex].openStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            stream = AudioSystem.getAudioInputStream(bis);
        } catch (Exception e) {
            log.error("Error reopening stream", e);
            pause();
        }
    }

    private void closeLine() {
        try {
            if (line != null) {
                line.stop();
                line.close();
            }
            if (stream != null) {
                stream.close();
            }
        } catch (Exception e) {
            log.trace("Error closing line", e);
        } finally {
            line = null;
            stream = null;
        }
    }

}

