package org.apache.sshd.server.shell.jump.textmode;

public interface TextMode {

    void onInput(byte[] input);

}
