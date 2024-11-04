package org.apache.sshd.jp.stream;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SinkBufferedInputStream extends InputStream implements SinkBuffer {

    private ByteBuffer buffer;
    private Lock lock = new ReentrantLock();
    private Condition available = lock.newCondition();

    public SinkBufferedInputStream(int capacity) {
        this.buffer = ByteBuffer.allocate(capacity);
    }

    @Override
    public int read() throws IOException {
        return buffer.get();
    }

    @Override
    public int read(byte[] b, int offset, int len) throws IOException {
        try {
            lock.lock();
            for (; ; ) {
                if (buffer.position() > offset) {
                    break;
                }
                try {
                    log.info("no more gold...");
                    available.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            this.buffer.flip();
            int length = len > this.buffer.limit() ? this.buffer.limit() : len;
            this.buffer.get(b, offset, length);
            this.buffer.compact();
            log.info("gold--{} to {}", length, this.buffer.position());
            return length;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int buffer(byte[] b, int offset, int length) throws IOException {
        try {
            lock.lock();
            if (this.buffer.capacity() < this.buffer.position() + length) {
                this.buffer = expandBuffer(this.buffer, length);
            }
            this.buffer.put(b, offset, length);
//            System.out.println("buffer size:" + buffer.position());
            log.info("signal gold++{} to {}", length, this.buffer.position());
            available.signalAll();
            return length;
        } finally {
            lock.unlock();
        }
    }

    private ByteBuffer expandBuffer(ByteBuffer buffer, int length) {
        int newCapacity = buffer.capacity() + length * 2;
        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
        newBuffer.put(buffer);
        return newBuffer;
    }
}
