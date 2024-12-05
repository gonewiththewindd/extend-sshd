package org.apache.sshd.server.shell.jump.textmode;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.InvertedShellWrapper;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;
import org.apache.sshd.utils.SshInputUtils;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class TopTextMode implements TextMode {

    private RemoteShellContext remoteShellContext;
    private OutputStream remoteIn;
    private InputStream remoteOut;
    private OutputStream clientOut;

    /**
     * help for interact:   1
     * help for window:     2
     */
    private int help;

    public TopTextMode(RemoteShellContext remoteShellContext, OutputStream remoteIn, InputStream remoteOut, OutputStream clientOut) {
        this.remoteShellContext = remoteShellContext;
        this.remoteIn = remoteIn;
        this.remoteOut = remoteOut;
        this.clientOut = clientOut;
    }

    @Override
    public void onInput(byte[] input) {

        Utils.writeAndFlush(remoteIn, input);
        // ctrl c and q for exit
        if (SshInputUtils.isCtrlC(input)) {
            log.info("ctrl c cause exit text mode.");
            remoteShellContext.commandMode = true;
        } else if (SshInputUtils.isH(input) || SshInputUtils.isQuestionMask(input)) {
            if (help < 2) {
                help++;
                log.info("help {}", help);
            }
        } else if (SshInputUtils.isQ(input)) {
            if (help == 0) {
                // exit help for interact page
                log.info("q cause exit text mode.");
                remoteShellContext.commandMode = true;
            } else if (help == 1) {
                // back
                help--;
                log.info("help {}", help);
            }
        } else if (SshInputUtils.isEscapeKey(input)) {
            if (help > 0) {
                help--;
            }
            log.info("help {}", help);
        } else if (help == 2 && SshInputUtils.isEnterChar(input)) {
            // exit help for window, back to init page
            help = 0;
            log.info("help {}", help);
        }
    }
}
