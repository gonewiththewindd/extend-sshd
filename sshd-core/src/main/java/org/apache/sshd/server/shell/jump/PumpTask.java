package org.apache.sshd.server.shell.jump;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
public class PumpTask extends Thread implements Runnable {

    private ChannelShell remoteChannelShell;
    private RemoteShellContext remoteShellContext;
    private InputStream remoteOut;
    private OutputStream clientOut;

    public PumpTask(ChannelShell remoteChannelShell, RemoteShellContext remoteShellContext, InputStream remoteOut, OutputStream clientOut) {
        this.remoteChannelShell = remoteChannelShell;
        this.remoteShellContext = remoteShellContext;
        this.remoteOut = remoteOut;
        this.clientOut = clientOut;
    }

    public void pump() {
        try {
            for (byte[] transfer = new byte[8192]; ; ) {
                if (remoteChannelShell.isClosed()) {
                    return;
                }
                if (remoteShellContext.pausePump) {
                    Thread.sleep(10);
                    continue;
                }
                int available = remoteOut.available();
                if (available > 0) {
                    // parse response and record
                    int len = remoteOut.read(transfer, 0, transfer.length);
                    if (remoteShellContext.commandMode) {
                        String resp = new String(transfer, 0, len, StandardCharsets.UTF_8);
                        log.info("receive remote channel data:{}", Arrays.toString(resp.toCharArray()));
                    }
                    // forward to client
                    clientOut.write(transfer, 0, len);
                    clientOut.flush();
                    continue;
                }
                Thread.sleep(10);
            }
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        this.pump();
    }
}
