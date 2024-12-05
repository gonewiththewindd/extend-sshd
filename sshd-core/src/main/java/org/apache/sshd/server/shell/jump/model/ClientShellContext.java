package org.apache.sshd.server.shell.jump.model;

import org.apache.sshd.server.Environment;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ClientShellContext {

    public LinkedList<Character> currentCommandBuffer = new LinkedList();
    public List<String> historyCommandList = new ArrayList<>();
    public int rowIndex;
    public int colIndex;
    public LinkedList<Character> bufferedCommand;
    public boolean insertMode;
    public String user;
    public Environment env;

    public ClientShellContext(Environment env) {
        this.user = env.getEnv().get("USER");
        this.env = env;
    }
}
