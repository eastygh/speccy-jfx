package spectrum.jfx.hardware.disk.wd1793.sound;

import javax.sound.sampled.*;
import java.io.File;

public final class WavLoopPlayer implements LoopPlayer, Runnable {

    private final File[] wavs;
    private final Thread thread;

    private volatile boolean running = true;
    private volatile boolean paused = true;

    private int currentIndex = -1;

    private AudioInputStream stream;
    private SourceDataLine line;

    private final Object lock = new Object();

    public WavLoopPlayer(File... wavs) {
        this.wavs = wavs;
        this.thread = new Thread(this, "wav-loop-player");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    @Override
    public void play(int index) {
        if (index < 0 || index >= wavs.length) {
            throw new IllegalArgumentException("bad wav index: " + index);
        }
        if (!paused) {
            return;
        }

        synchronized (lock) {
            if (currentIndex != index) {
                closeLine();
                currentIndex = index;
                openLine(wavs[index]);
            }

            paused = false;
            lock.notifyAll();
        }
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void stop() {
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
                while (paused && running) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            if (!running || line == null || stream == null) {
                continue;
            }

            try {
                int read = stream.read(buffer, 0, buffer.length);
                if (read == -1) {
                    // зацикливаем
                    reopenStream();
                    continue;
                }
                line.write(buffer, 0, read);
            } catch (Exception e) {
                e.printStackTrace();
                pause();
            }
        }
    }

    private void openLine(File wav) {
        try {
            stream = AudioSystem.getAudioInputStream(wav);
            AudioFormat format = stream.getFormat();

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
        } catch (Exception e) {
            throw new RuntimeException("cannot open wav: " + wav, e);
        }
    }

    private void reopenStream() {
        try {
            stream.close();
            stream = AudioSystem.getAudioInputStream(wavs[currentIndex]);
        } catch (Exception e) {
            e.printStackTrace();
            pause();
        }
    }

    private void closeLine() {
        try {
            if (line != null) {
                line.drain();
                line.stop();
                line.close();
            }
            if (stream != null) {
                stream.close();
            }
        } catch (Exception ignored) {
        } finally {
            line = null;
            stream = null;
        }
    }
}

