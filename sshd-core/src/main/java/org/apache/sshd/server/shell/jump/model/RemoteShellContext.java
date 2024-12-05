package org.apache.sshd.server.shell.jump.model;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.jump.textmode.TextMode;
import org.apache.sshd.server.shell.jump.textmode.Utils;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.utils.SshInputUtils;

import java.util.Arrays;
import java.util.LinkedList;

@Slf4j
public class RemoteShellContext {

    public final Asset asset;

    public volatile boolean commandMode = true;
    public LinkedList<Character> currentCommandBuffer = new LinkedList();
    //        public List<String> historyCommandList = new ArrayList<>();
    //        public int rowIndex;
    public int colIndex;
    //        public LinkedList<Character> bufferedCommand;
    public boolean pausePump;
    public boolean insertMode = true;
    public boolean tabYesOrNo;
    public TextMode editMode;

    public String user;
    public String tty;
    public String command;

    public RemoteShellContext(String tty, String username, Asset asset) {
        this.tty = tty;
        this.user = username;
        this.asset = asset;
    }

    public void exitCommandMode(String command) {
        log.warn("command '{}' cause exit command mode", command);
        this.commandMode = false;
        this.command = command;
    }

    public void enterCommandMode(char[] key) {
        Utils.trimLeading(key, (char) SshInputUtils.DELETE_CHAR);
        Utils.trimLeading(key, (char) SshInputUtils.BACKSPACE_CHAR);
        key = Utils.replaceKey(key, SshInputUtils.ENTER_CHAR, '+');
        log.warn("key '{}' cause enter command mode", Arrays.toString(key));
        this.commandMode = true;
        this.command = null;
    }

    public void addChars(char[] charArray) {
        for (int i = 0; i < charArray.length; i++) {
            addChar(charArray[i]);
        }
    }

    public void addChar(char c) {
        if (colIndex < currentCommandBuffer.size()) {
            if (insertMode) {
                currentCommandBuffer.add(c);
            } else {
                currentCommandBuffer.set(colIndex, c);
            }
        } else {
            currentCommandBuffer.add(c);
        }
        colIndex++;
    }
}
