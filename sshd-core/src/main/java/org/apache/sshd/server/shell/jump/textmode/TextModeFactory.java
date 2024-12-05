package org.apache.sshd.server.shell.jump.textmode;

import org.apache.sshd.server.shell.InvertedShellWrapper;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TextModeFactory {

    private final static Set<String> TEXT_PROC_LIST = new HashSet<>(Arrays.asList("vi", "vim", "tail", "tailf", "less", "top"));

    public static TextMode createTextMode(String proc,
                                          RemoteShellContext remoteShellContext,
                                          OutputStream remoteIn,
                                          InputStream remoteOut,
                                          OutputStream clientOut) {

        switch (proc) {
            case "vi":
            case "vim":
                return new VimTextMode(proc, remoteShellContext, remoteIn, remoteOut, clientOut);
            case "tail":
            case "tailf":
                return new TailfTextMode(remoteShellContext, remoteIn, remoteOut, clientOut);
            case "less":
                return new LessTextMode(remoteShellContext, remoteIn, remoteOut, clientOut);
            case "top":
                return new TopTextMode(remoteShellContext, remoteIn, remoteOut, clientOut);
            default:
                throw new IllegalArgumentException("Unknown text mode: " + proc);
        }
    }

    public static boolean isTextProc(String[] segments) {

        if (TEXT_PROC_LIST.contains(segments[0])) {
            return "tail".equals(segments[0]) ? hasOption(segments, "-f") : true;
        }
        return false;
    }

    private static boolean hasOption(String[] segments, String option) {
        return Arrays.stream(segments).filter(s -> s.equals(option)).findFirst().isPresent();
    }

}
