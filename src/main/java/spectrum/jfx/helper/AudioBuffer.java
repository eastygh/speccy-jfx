package spectrum.jfx.helper;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock-free audio buffer implementation using a circular buffer, for two threads.
 */
public class AudioBuffer {

    private final short[] buffer;
    private int head = 0;
    private int tail = 0;
    private int count = 0;

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public AudioBuffer(int capacity) {
        this.buffer = new short[capacity];
    }


    public boolean offer(short sample) {
        lock.lock();
        try {
            if (count == buffer.length) {
                // second try
                try {
                    notFull.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (count == buffer.length) {
                    // fail
                    return false;
                }
            }
            buffer[tail] = sample;
            tail = (tail + 1) % buffer.length;
            count++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        return true;
    }

    public short take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            short sample = buffer[head];
            head = (head + 1) % buffer.length;
            count--;
            notFull.signal();
            return sample;
        } finally {
            lock.unlock();
        }
    }

}