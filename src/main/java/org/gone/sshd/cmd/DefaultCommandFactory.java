package org.gone.sshd.cmd;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DefaultCommandFactory implements CommandFactory {
    @Override
    public Command createCommand(ChannelSession channel, String command) throws IOException {

        return null;
    }

    public static class DefaultCommand implements Command {

        @Override
        public void setExitCallback(ExitCallback callback) {

        }

        @Override
        public void setErrorStream(OutputStream err) {

        }

        @Override
        public void setInputStream(InputStream in) {

        }

        @Override
        public void setOutputStream(OutputStream out) {

        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {

        }

        @Override
        public void destroy(ChannelSession channel) throws Exception {

        }
    }

}
