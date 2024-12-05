package org.apache.sshd.server.shell.jump.textmode;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.jump.model.ClientShellContext;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Utils {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int DEFAULT_TIMEOUT = 300;
    public static final String LOCAL_COMMAND_STASH_PATH = "D:\\Users\\Desktop\\history_command.txt";
    // user asset ip-address account time command
    public static final String COMMAND_FORMAT = "%-16s %-16s %-16s %-16s %-20s %s\n";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final ExecutorService POOL_EXECUTOR = Executors.newFixedThreadPool(1);

    public static char[] unboxed(List<Character> list) {
        char[] chars = new char[list.size()];
        for (int i = 0; i < list.size(); i++) {
            chars[i] = list.get(i);
        }
        return chars;
    }

    public static byte[] blockReadCommandResult(InputStream in) {
        int len;
        byte[] bytes = new byte[8092];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        do {
            try {
                len = in.read(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(bytes, 0, len);
        } while (len == bytes.length);
        return buffer.toByteArray();
    }

    public static byte[] readCommandResultWithTimeout(InputStream in, long... t) {
        long timeout = Objects.nonNull(t) && t.length > 0 && t[0] > 0 ? t[0] : DEFAULT_TIMEOUT;
        long tt = timeout;
        for (; timeout > 0; ) {
            try {
                long s = System.currentTimeMillis();
                if (in.available() > 0) {
                    byte[] buffer = new byte[in.available()];
                    int len = in.read(buffer);
                    return Arrays.copyOfRange(buffer, 0, len);
                }
                Thread.sleep(10);
                timeout -= (System.currentTimeMillis() - s);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        log.error("read command result TIMEOUT after {}ms", tt);

        return EMPTY_BYTE_ARRAY;
    }

    public static void writeAndFlush(OutputStream out, byte[]... commands) {
        try {
            for (int i = 0; i < commands.length; i++) {
                out.write(commands[i]);
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static char[] trimLeading(char[] unboxed, char c) {
        int i = 0;
        for (; i < unboxed.length; i++) {
            if (c != unboxed[i]) {
                break;
            }
        }
        return Arrays.copyOfRange(unboxed, i, unboxed.length);
    }

    public static byte[] trimLeading(byte[] unboxed, byte c) {
        int i = 0;
        for (; i < unboxed.length; i++) {
            if (c != unboxed[i]) {
                break;
            }
        }
        return Arrays.copyOfRange(unboxed, i, unboxed.length);
    }

    public static char[] replaceKey(char[] key, char target, char replacement) {
        char[] chars = Arrays.copyOf(key, key.length);
        for (int i = 0; i < chars.length; i++) {
            if (key[i] == target) {
                chars[i] = replacement;
            }
        }
        return chars;
    }

    public static void flushCommandToLocal(RemoteShellContext remoteShellContext, ClientShellContext clientShellContext, String command) {
        POOL_EXECUTOR.execute(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(LOCAL_COMMAND_STASH_PATH), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                // user asset ip-address account time command
                String log = String.format(
                        COMMAND_FORMAT,
                        clientShellContext.user,
                        remoteShellContext.asset.getName(),
                        remoteShellContext.asset.getAddress(),
                        remoteShellContext.asset.getUsername(),
                        LocalDateTime.now().format(FORMATTER),
                        command
                );
                writer.write(log);
                writer.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public static boolean startWith(byte[] bytes, byte[] prefix) {
        if (bytes.length == 0) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] trimIfStartWith(byte[] bytes, byte[] prefix) {
        if (startWith(bytes, prefix)) {
            return Arrays.copyOfRange(bytes, prefix.length, bytes.length);
        }
        return bytes;
    }
}
