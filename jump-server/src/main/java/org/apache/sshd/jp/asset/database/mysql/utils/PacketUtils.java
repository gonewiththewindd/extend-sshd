package org.apache.sshd.jp.asset.database.mysql.utils;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class PacketUtils {

    public static CharSequence readStringTilNull(ByteBuf msg) {
        int length = 0;
        int i = msg.readerIndex();
        while (msg.getByte(i++) != 0) {
            length++;
        }
        CharSequence charSequence = msg.readCharSequence(length + 1, StandardCharsets.UTF_8);
        return charSequence.subSequence(0, length);
//        return msg.readCharSequence(length, StandardCharsets.UTF_8);
    }


    public static long readEncodedInt(ByteBuf msg) {
        short firstByte = msg.readUnsignedByte();
        if (firstByte == 0xfc) {
            return 0xfc + msg.readUnsignedShortLE();
        } else if (firstByte == 0xfd) {
            return 0xfd + msg.readUnsignedMediumLE();
        } else if (firstByte == 0xfe) {
            return 0xfe + msg.readLongLE();
        } else {
            return firstByte;
        }
    }

    public static void main(String[] args) {

    }

    public static byte[] encodeIntLE(long value) {
        if (value < 251) {
            return new byte[]{(byte) value};
        } else if (value < 1 << 16) {
            return encodeIntLE(value, 3, (byte) 0xfc);
        } else if (value < 1 << 24) {
            return encodeIntLE(value, 4, (byte) 0xfd);
        } else if (value <= Long.MAX_VALUE) {
            return encodeIntLE(value, 8, (byte) 0xfe);
        } else {
            throw new IllegalArgumentException("");
        }
    }

    private static byte[] encodeIntLE(long value, int length, byte first) {
        byte[] bytes = new byte[length];
        bytes[0] = first;
        long rest = value - first;
        for (int i = 1; i < length; i++) {
            int shift = (i - 1) * 8;
            bytes[i] = (byte) ((rest & (0xff << shift)) >> shift);
        }
        return bytes;
    }
}
