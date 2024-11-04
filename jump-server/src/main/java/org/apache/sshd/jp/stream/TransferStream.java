package org.apache.sshd.jp.stream;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

@Slf4j
public class TransferStream extends OutputStream {

    private SinkBuffer buffer;

    public TransferStream(SinkBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void write(int b) throws IOException {
        buffer.buffer(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.buffer(b, off, len);
    }

    public static void main(String[] args) throws InterruptedException {

        Random random = new Random();
        SinkBufferedInputStream in = new SinkBufferedInputStream(1024);
        TransferStream out = new TransferStream(in);
        new Thread(() -> {
            for (; ; ) {
                byte[] bytes = new byte[random.nextInt(10240) + 1];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = nextByte();
                }
                try {
                    out.write(bytes, 0, bytes.length);
                    Thread.sleep(2000);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                for (; ; ) {
                    try {
                        byte[] bytes = new byte[random.nextInt(256) + 1];
                        int read = in.read(bytes, 0, bytes.length);
                        Thread.sleep(1000);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        Thread.sleep(Long.MAX_VALUE);
    }

    public static long counter = 0;

    private static byte nextByte() {
        return (byte) (counter++ & 127);
    }
}
