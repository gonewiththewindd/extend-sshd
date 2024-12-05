package org.apache.sshd.server.shell.jump.textmode;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;
import org.apache.sshd.utils.SshInputUtils;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class TailfTextMode implements TextMode {

    private RemoteShellContext remoteShellContext;
    private OutputStream remoteIn;
    private InputStream remoteOut;
    private OutputStream clientOut;

    public TailfTextMode(RemoteShellContext remoteShellContext, OutputStream remoteIn, InputStream remoteOut, OutputStream clientOut) {
        this.remoteShellContext = remoteShellContext;
        this.remoteIn = remoteIn;
        this.remoteOut = remoteOut;
        this.clientOut = clientOut;
    }

    @Override
    public void onInput(byte[] input) {

        Utils.writeAndFlush(remoteIn, input);
        // ctrl c for exit
        if (SshInputUtils.isCtrlC(input)) {
            log.info("Ctrl-C cause exit text mode.");
            remoteShellContext.commandMode = true;
        }
    }
}
