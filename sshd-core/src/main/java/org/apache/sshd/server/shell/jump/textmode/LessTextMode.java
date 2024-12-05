package org.apache.sshd.server.shell.jump.textmode;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.InvertedShellWrapper;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;
import org.apache.sshd.utils.SshInputUtils;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class LessTextMode implements TextMode {

    private RemoteShellContext remoteShellContext;
    private OutputStream remoteIn;
    private InputStream remoteOut;
    private OutputStream clientOut;

    public LessTextMode(RemoteShellContext remoteShellContext, OutputStream remoteIn, InputStream remoteOut, OutputStream clientOut) {
        this.remoteShellContext = remoteShellContext;
        this.remoteIn = remoteIn;
        this.remoteOut = remoteOut;
        this.clientOut = clientOut;
    }

    @Override
    public void onInput(byte[] input) {

        Utils.writeAndFlush(remoteIn, input);
        // q for exit
        if (SshInputUtils.isQ(input)) {
            log.info("q cause exit text mode.");
            remoteShellContext.commandMode = true;
        }
    }
}
