package org.apache.sshd.server.shell.jump.textmode;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;
import org.apache.sshd.utils.SshInputUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;

import static org.apache.sshd.utils.SshInputUtils.EDIT_MODE_EXIT_FORCE_WQ;

@Slf4j
public class VimTextMode implements TextMode {

    private String proc;
    /**
     * 1:   terminate
     * 2:   insert
     * 3:   command
     */
    private short phase = 1;
    private boolean insertMode;
    private final LinkedList<Character> currentCommandBuffer = new LinkedList();
    private int colIndex;
    private final RemoteShellContext remoteShellContext;

    private final InputStream remoteOut;
    private final OutputStream remoteIn;
    private final OutputStream clientOut;

    public VimTextMode(String proc, RemoteShellContext remoteShellContext, OutputStream remoteIn, InputStream remoteOut, OutputStream clientOut) {
        this.proc = proc;
        this.remoteShellContext = remoteShellContext;
        this.remoteIn = remoteIn;
        this.remoteOut = remoteOut;
        this.clientOut = clientOut;
    }

    public void onInput(byte[] input) {

        switch (this.phase) {
            case 1:
                //打破暂停状态指令，i 进入插入模式， : 进入命令模式
                if (enterEditMode(input)) {
                    log.info("Enter edit mode");
                    this.phase = 2;
                } else if (enterCommandMode(input)) {
                    log.info("Enter command mode");
                    this.phase = 3;
                }
                Utils.writeAndFlush(remoteIn, input);
                break;
            case 2:
                if (SshInputUtils.isEscapeKey(input)) {
                    log.info("Enter terminate mode");
                    this.phase = 1;
                }
                /*remoteShellContext.pausePump = true;
                Utils.writeAndFlush(remoteIn, input);
                byte[] editResp = Utils.blockReadCommandResult(remoteOut);
                log.info("edit resp:{}", new String(editResp, StandardCharsets.UTF_8));
                Utils.writeAndFlush(clientOut, editResp, SshInputUtils.CMD_CLEAR_FROM_CURSOR_TO_LINE_END);
                remoteShellContext.pausePump = false;*/
                Utils.writeAndFlush(remoteIn, input);
                break;
            case 3:
                if (SshInputUtils.isDeleteKey(input)) {
                    if (!currentCommandBuffer.isEmpty()) {
                        currentCommandBuffer.remove(--colIndex);
                    } else {
                        log.info("no command deletable cause exit command mode and enter terminate mode");
                        this.phase = 1;
                    }
                    Utils.writeAndFlush(remoteIn, input);
                } else if (SshInputUtils.isLeftRightKey(input)) {
                    if (SshInputUtils.isLeftKey(input) && this.colIndex > 0) {
                        this.colIndex--;
                    }
                    if (SshInputUtils.isRightKey(input) && this.colIndex < currentCommandBuffer.size()) {
                        this.colIndex++;
                    }
                    Utils.writeAndFlush(remoteIn, input);
                } else if (SshInputUtils.isInsertKey(input)) {
                    this.insertMode = !this.insertMode;
                    Utils.writeAndFlush(remoteIn, input);
                } else if (SshInputUtils.isEnterChar(input)) {
                    if (!currentCommandBuffer.isEmpty()) {
                        char[] unboxed = Utils.unboxed(currentCommandBuffer);
                        String command = new String(Utils.trimLeading(unboxed, SshInputUtils.EDIT_MODE_COMMAND_CHAR));
                        log.info("vim command:{}", command);
                        if (isStrongExitCommand(unboxed)) {
                            log.info("vim strong exit command '{}' cause exit text mode.", command);
                            remoteShellContext.commandMode = true;
                            Utils.writeAndFlush(remoteIn, input);
                        } else if (isWeakExitCommand(unboxed)) {
                            remoteShellContext.pausePump = true;
                            Utils.writeAndFlush(remoteIn, input);
                            byte[] bytes = Utils.readCommandResultWithTimeout(remoteOut);
                            log.info("vim weak exit command result:{}", new String(bytes));
                            String resp = new String(bytes);
                            if (!resp.contains("No write since last change")
                                    && !resp.contains("已修改但尚未保存")
                                    && !resp.contains("read only")) {
                                log.info("vim weak exit command '{}' exec succeed cause exit text mode.", command);
                                remoteShellContext.commandMode = true;
                            } else {
                                log.info("enter terminate mode");
                                this.phase = 1;
                            }
                            Utils.writeAndFlush(clientOut, bytes);
                            remoteShellContext.pausePump = false;
                        } else {
                            this.phase = 1;
                            Utils.writeAndFlush(remoteIn, input);
                        }
                        currentCommandBuffer.clear();
                        colIndex = 0;
                    }
                } else {
                    if (colIndex < currentCommandBuffer.size()) {
                        if (this.insertMode) {
                            currentCommandBuffer.set(colIndex++, (char) input[0]);
                        } else {
                            currentCommandBuffer.add(colIndex++, (char) input[0]);
                        }
                    } else {
                        currentCommandBuffer.add(colIndex++, (char) input[0]);
                    }
                    Utils.writeAndFlush(remoteIn, input);
                }
                break;
        }

        //
//        Utils.writeAndFlush(remoteIn, input);
    }


    public boolean isCommandMode() {
        return this.phase == 3;
    }

    public boolean enterCommandMode(byte[] input) {
        return input[0] == SshInputUtils.EDIT_MODE_COMMAND_CHAR;
    }


    public boolean exitCommandMode() {
        return false;
    }


    public boolean isEditMode() {
        return this.phase == 2;
    }

    public boolean enterEditMode(byte[] input) {
        return input[0] == SshInputUtils.EDIT_MODE_INSERT_CHAR;
    }


    public boolean isStrongExitCommand(char[] raw) {
        return Arrays.equals(SshInputUtils.EDIT_MODE_EXIT_FORCE_Q, raw) || Arrays.equals(EDIT_MODE_EXIT_FORCE_WQ, raw);
    }

    public boolean isWeakExitCommand(char[] raw) {
        return Arrays.equals(SshInputUtils.EDIT_MODE_EXIT_Q, raw) || Arrays.equals(SshInputUtils.EDIT_MODE_EXIT_WQ, raw);
    }
}
