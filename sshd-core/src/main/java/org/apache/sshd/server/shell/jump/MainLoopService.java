package org.apache.sshd.server.shell.jump;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.ChannelOutputStream;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.shell.InvertedShell;
import org.apache.sshd.server.shell.jump.model.ClientShellContext;
import org.apache.sshd.server.shell.jump.model.RemoteShellContext;
import org.apache.sshd.server.shell.jump.textmode.Utils;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.apache.sshd.utils.SshInputUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.sshd.server.shell.jump.textmode.Utils.*;
import static org.apache.sshd.utils.SshInputUtils.*;

@Slf4j
public class MainLoopService {

    public static final String TERMINAL_TTY_CMD = "tty\r";
    public static final String TERMINAL_PID_CMD = "ps -ef | grep sshd | grep -v grep | grep $tty | awk '{print $2}'\r";
    public static final String PS_COMMAND_CMD = "ps -ef | grep -e \"$cmd\" | grep -e \"$tty\" | grep -v 'grep'";
    public static final Set<byte[]> SPECIAL_KEY = new HashSet<>() {{
        add(new byte[]{ESCAPE_CHAR});
        add(new byte[]{ENTER_CHAR});
        add(new byte[]{CTRL_C_CHAR});
        add(new byte[]{CTRL_X_CHAR});
        add(new byte[]{'q'});
        add(new byte[]{'a'}); // abort on vim edit conflict
    }};

    public final Map<String, Function<Object, String>> commandMap = new HashMap<>();

    private volatile boolean localMode = true;
    private volatile boolean pending;

    private final InvertedShell shell;
    private InputStream clientIn;
    private OutputStream clientOut;

    private InputStream remoteOut;
    private OutputStream remoteIn;

    private volatile ClientShellContext clientShellContext;
    private volatile RemoteShellContext remoteShellContext;

    private volatile ChannelShell remoteChannelShell;

    public MainLoopService(InvertedShell shell, InputStream clientIn, OutputStream clientOut, Environment env) {
        this.shell = shell;
        this.clientIn = clientIn;
        this.clientOut = clientOut;
        clientShellContext = new ClientShellContext(env);

        this.commandMap.put("p", aaa -> AssetService.listAssets());
        this.commandMap.put("q", this::exit);
        this.commandMap.put("exit", this::exit);
        this.commandMap.put("cls", this::clear);
        this.commandMap.put("clear", this::clear);

    }

    public void onStartup() throws IOException {
//        logShellInfo(3000);
        sendAssets();
        openInsertMode();
    }


    private void sendAssets() throws IOException {
        String assets = AssetService.listAssets();
        byte[] bytes = assets.getBytes(StandardCharsets.UTF_8);
        // 发送用户资产列表
        clientOut.write(bytes);
        clientOut.flush();
    }

    private void logShellInfo(long timeout) throws IOException {
        /*long start = System.currentTimeMillis();
        for (; ; ) {
            if (timeout < 0) {
                clientOut.write("timeout".getBytes(StandardCharsets.UTF_8));
                return;
            }
            // ignore read out and error
            int shellOutAvailable = shellOut.available();
            if (shellOutAvailable > 0) {
                byte[] outBuffer = new byte[shellOutAvailable];
                shellOut.read(outBuffer, 0, shellOutAvailable);
                break;
            }
            int shellErrorAvailable = shellErr.available();
            if (shellErrorAvailable > 0) {
                byte[] errorBuffer = new byte[shellErrorAvailable];
                shellErr.read(errorBuffer, 0, shellErrorAvailable);
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            timeout -= System.currentTimeMillis() - start;
        }*/
    }

    private void openInsertMode() throws IOException {
        clientOut.write(CMD_INSERT_MODE_OPEN);
        clientOut.flush();
        clientShellContext.insertMode = true;
    }

    public void debug() {
        MainLoopService mainLoopService = this;
        new Thread(() -> {
            for (int i = 0; i < Long.MAX_VALUE; i++) {
                RemoteShellContext remoteShellContext1 = mainLoopService.remoteShellContext;
                log.debug("");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    public void loop() throws IOException {
        // 输入命令缓存和解析
        int clientInAvailable = clientIn.available();
        if (clientInAvailable <= 0) {
            return;
        }
        byte[] buffer = new byte[clientInAvailable];
        int len = clientIn.read(buffer, 0, clientInAvailable);
        if (len < 0) {
            return;
        }
        String key = new String(buffer, 0, len, StandardCharsets.UTF_8);
        // 键盘键输入处理
        processKey(buffer, key);
    }

    private void processKey(byte[] buffer, String key) throws IOException {
        if (pending) {
            return;
        }
        if (localMode) {
            processLocalKey(buffer, key);
        } else {
            processRemoteKey(buffer, key);
        }
    }

    private void processRemoteKey(byte[] buffer, String key) throws IOException {

        if (remoteShellContext.commandMode) {
            // 处理特殊键，比如方向上下选择键，左右光标移动键，删除键，空指令换行键，空格
            char[] charArray = key.toCharArray();
            if (remoteShellContext.tabYesOrNo) {
                processTabYesOrNo(buffer);
            } else if (SshInputUtils.isUpDownKey(buffer)) {
                processRemoteUpDownKey(buffer);
            } else if (SshInputUtils.isLeftRightKey(buffer)) {
                processRemoteLeftRightKey(buffer, key, charArray);
            } else if (SshInputUtils.isTabKey(charArray)) {
                processRemoteTabKey(buffer, key, charArray);
            } else if (SshInputUtils.isDeleteKey(buffer)) {
                processRemoteDeleteKey(buffer);
            } else if (SshInputUtils.isInsertKey(buffer)) {
                processRemoteInsertKey(buffer);
            } else {
                processRemoteNormalKey(buffer, key, charArray);
            }
        } else {
            // 特殊键监测
            // TODO ctrl z
            if (isSpecialKey(buffer)) {
                remoteShellContext.pausePump = true;
                Utils.writeAndFlush(remoteIn, buffer);
                byte[] bytes = readCommandResultWithTimeout(remoteOut);
                Utils.writeAndFlush(clientOut, bytes);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (hasDestroyPseudoTerminal()) {
                    remoteShellContext.enterCommandMode(new String(buffer).toCharArray());
                }
                remoteShellContext.pausePump = false;
            } else {
                Utils.writeAndFlush(remoteIn, buffer);
            }
//            remoteShellContext.editMode.onInput(buffer);
        }
    }

    private void processRemoteLeftRightKey(byte[] buffer, String key, char[] charArray) {
        // TODO 下标移动异常
        this.remoteShellContext.colIndex += isLeftKey(buffer) ? -1 : 1;
        if (this.remoteShellContext.colIndex < 0) {
            this.remoteShellContext.colIndex = 0;
        } else if (this.remoteShellContext.colIndex > remoteShellContext.currentCommandBuffer.size()) {
            this.remoteShellContext.colIndex = remoteShellContext.currentCommandBuffer.size();
        } else {
            writeAndFlush(remoteIn, buffer);
        }
    }

    private void processTabYesOrNo(byte[] buffer) {
        if (buffer.length == 1 && CONFIRM.stream().anyMatch(b -> b == buffer[0])) {
            Utils.writeAndFlush(remoteIn, buffer);
            remoteShellContext.tabYesOrNo = false;
        }
    }

    private void processRemoteTabKey(byte[] buffer, String key, char[] charArray) {
        remoteShellContext.pausePump = true;
        writeAndFlush(remoteIn, buffer);

        byte[] bytes = readCommandResultWithTimeout(remoteOut);
        writeAndFlush(clientOut, /*CMD_CLEAR_FROM_CURSOR_TO_LINE_END, */bytes);
        String promptList = new String(bytes, StandardCharsets.UTF_8);
        // TODO 智能提示TAB+光标移动有问题(中间智能提示)
        if (!promptList.contains("Display all")) {
            if (bytes.length > 0 && bytes[0] == BEL_CHAR) {
                // 自动填充
                for (int i = 1; i < bytes.length; i++) {
                    remoteShellContext.addChar((char) bytes[i]);
                }
            } else if (promptList.startsWith("\r\n")) {
                // 提示词和光标后续内容完全匹配，直接更新索引值
                remoteShellContext.colIndex = remoteShellContext.currentCommandBuffer.size();
            } else {
                List<String> filteredPromptList = Arrays.stream(promptList.split(" ")).filter(pmpt -> !pmpt.trim().equalsIgnoreCase("")).toList();
                if (filteredPromptList.size() == 1) {
                    // 自动填充
                    String prompt = filteredPromptList.get(0).concat(" ");
                    remoteShellContext.addChars(prompt.toCharArray());
                }
            }
        } else {
            remoteShellContext.tabYesOrNo = true;
        }
        log.info("tab prompt: {}", promptList);
        remoteShellContext.pausePump = false;
    }

    private boolean isExitEditMode(byte[] buffer) {
//        return Arrays.equals(buffer, EDIT_MODE_EXIT_Q)
        return false;
    }

    private void processRemoteDeleteKey(byte[] buffer) throws IOException {

        if (!localMode) {
            if (!remoteShellContext.currentCommandBuffer.isEmpty()) {
                if (--remoteShellContext.colIndex >= 0) {
                    remoteShellContext.currentCommandBuffer.remove(remoteShellContext.colIndex);
                    remoteShellContext.pausePump = true;
                    Utils.writeAndFlush(remoteIn, buffer);
                    Utils.writeAndFlush(clientOut, CMD_DELETE);
                    // ignore remote terminal response
                    byte[] bytes = readCommandResultWithTimeout(remoteOut);
//                    writeAndFlush(remoteIn,  new byte[]{ESCAPE_CHAR, '[', 'D'});
                    remoteShellContext.pausePump = false;
                }
                if (remoteShellContext.colIndex < 0) {
                    remoteShellContext.colIndex = 0;
                }
            }
        }

    }

    private void processRemoteInsertKey(byte[] buffer) throws IOException {
        if (!localMode) {
            Utils.writeAndFlush(remoteIn, buffer);
            remoteShellContext.insertMode = !remoteShellContext.insertMode;
        }
    }

    private void processRemoteNormalKey(byte[] buffer, String key, char[] charArray) throws IOException {

        // 普通字符输入，回传，考虑插入模式的情况，覆盖还是追加
        if (key.length() > 0 && key.charAt(0) != SshInputUtils.ENTER_CHAR) {
            remoteShellContext.addChars(charArray);
        }
        if (SshInputUtils.isEnterChar(buffer)) {
            String command = new String(unboxed(remoteShellContext.currentCommandBuffer));
            if (!remoteShellContext.currentCommandBuffer.isEmpty()) {
                // 指令
                command = command.trim();
                // 指令拦截和记录
                Utils.flushCommandToLocal(remoteShellContext, clientShellContext, command);
                // 指令执行
                processRemoteCommand(command, buffer);
            } else {
                Utils.writeAndFlush(remoteIn, buffer);
            }
        } else {
            remoteShellContext.pausePump = true;
            Utils.writeAndFlush(remoteIn, buffer);
            byte[] bytes = readCommandResultWithTimeout(remoteOut);
            // 先通知终端移除当前光标后续内容，再进行追加
            byte[] trimBytes = trimIfStartWith(bytes, RIGHT_CHAR);
            if (startWith(bytes, RIGHT_CHAR)) {
                Utils.writeAndFlush(clientOut, CMD_CLEAR_FROM_CURSOR_TO_LINE_END, buffer, trimBytes);
            } else {
                Utils.writeAndFlush(clientOut, CMD_CLEAR_FROM_CURSOR_TO_LINE_END, bytes);
            }
            remoteShellContext.pausePump = false;
        }
    }

    private void processRemoteCommand(String command, byte[] buffer) {
        log.info("process remote command:{}", command);
        remoteShellContext.pausePump = true;
        // exit cause the resource be free
        writeAndFlush(remoteIn, buffer);
        byte[] commandEcho = readCommandResultWithTimeout(remoteOut);
        writeAndFlush(clientOut, commandEcho);
        // 监测指令是否创建伪终端 离开命令行界面
        if (!localMode) {
            if (hasCreatePseudoTerminalByCommand(command)) {
                remoteShellContext.exitCommandMode(command);
            }
            remoteShellContext.pausePump = false;
            clearRemoteContextAndRecord();
        }
    }

    private boolean hasCreatePseudoTerminalByCommand(String command) {

        String tty = remoteShellContext.tty;
        String detectCmd = PS_COMMAND_CMD.replace("$cmd", command).replace("$tty", tty);
        String detectResult = executeRemoteCommandIgnoreExitStatus(detectCmd);
        log.info("pseudo terminal detect result:\n{}", detectResult);
        if (Objects.nonNull(detectResult) && detectResult.length() > 0) {
            return Arrays.stream(detectResult.split(" "))
                    .filter(seg -> !seg.trim().equalsIgnoreCase(""))
                    .collect(Collectors.toList())
                    .get(5)
                    .equalsIgnoreCase(tty);
        }
        return false;

    }

    private String executeRemoteCommandIgnoreExitStatus(String command) {
        try (ByteArrayOutputStream err = new ByteArrayOutputStream()) {
            String result = remoteChannelShell.getClientSession().executeRemoteCommand(command, err, StandardCharsets.UTF_8);
            if (err.size() > 0) {
                log.info("execute remote command with error:{}", new String(err.toByteArray(), StandardCharsets.UTF_8));
            }
            return result;
        } catch (IOException e) {
            if (e instanceof RemoteException re) {
                // grep 指令退出状态码1 意味着没有找到匹配的记录
                if (re.detail instanceof ServerException se && isGrepCommand(command) && exitWithStatus(se, 1)) {
                    return EMPTY_STRING;
                } else {
                    log.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean exitWithStatus(ServerException se, int i) {
        return String.valueOf(1).equalsIgnoreCase(se.getMessage());
    }

    private boolean isGrepCommand(String command) {
        String[] segments = command.split("\\|");
        String[] maybeGrepCommand = segments[segments.length - 1].trim().split(" ");
        return maybeGrepCommand[0].equalsIgnoreCase("grep");
    }

    private boolean isSpecialKey(byte[] key) {
        return SPECIAL_KEY.stream().anyMatch(sk -> Arrays.equals(sk, key));
    }

    private boolean hasDestroyPseudoTerminal() {
        String tty = remoteShellContext.tty;
        String detectCmd = PS_COMMAND_CMD.replace("$cmd", remoteShellContext.command).replace("$tty", tty);
        String detectResult = executeRemoteCommandIgnoreExitStatus(detectCmd);
        log.info("pseudo terminal detect result:\n{}", detectResult);
        if (Objects.nonNull(detectResult) && detectResult.length() > 0) {
            return !Arrays.stream(detectResult.split(" "))
                    .filter(seg -> !seg.trim().equalsIgnoreCase(""))
                    .collect(Collectors.toList())
                    .get(5)
                    .equalsIgnoreCase(tty);
        }
        return true;
    }

    private boolean alternateBufferSwitch(byte[] bytes) {
        for (int i = 0; i < ALTERNATE_BUFFER_SWITCHING.length; i++) {
            if (ALTERNATE_BUFFER_SWITCHING[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean alternateBufferSwitchExit(byte[] bytes) {
        for (int i = 0; i < ALTERNATE_BUFFER_EXIT.length; i++) {
            if (ALTERNATE_BUFFER_EXIT[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private void processRemoteUpDownKey(byte[] buffer) throws IOException {
        // 停止推流
        remoteShellContext.pausePump = true;
        Utils.writeAndFlush(remoteIn, buffer);
        // 获取方向键命令选择结果
        byte[] raw = readCommandResultWithTimeout(remoteOut);
        byte[] trimRaw = trimLeading(raw, BACKSPACE_CHAR);
        String history = new String(trimRaw, StandardCharsets.UTF_8);
        // 指令预选
        log.info("select command:{}", history);
        remoteShellContext.currentCommandBuffer.clear();
        remoteShellContext.colIndex = 0;
        if (!(raw.length == 1 && raw[0] == BEL_CHAR)) {
//            remoteShellContext.currentCommandBuffer.addAll(history.chars().mapToObj(c -> (char) c).collect(Collectors.toList()));
//            remoteShellContext.colIndex = history.length();
            remoteShellContext.addChars(history.toCharArray());
        } else {
            remoteShellContext.colIndex = 0;
        }

        Utils.writeAndFlush(clientOut, raw, CMD_CLEAR_FROM_CURSOR_TO_LINE_END);
        // 恢复推流
        remoteShellContext.pausePump = false;
    }

    private void processLocalKey(byte[] buffer, String key) throws IOException {
        // 处理特殊键，比如方向上下选择键，左右光标移动键，删除键，空指令换行键，空格
        char[] charArray = key.toCharArray();
        if (SshInputUtils.isUpDownKey(buffer)) {
            processLocalUpDownKey(buffer, charArray);
        } else if (SshInputUtils.isLeftRightKey(buffer)) {
            processLocalLeftRightKey(buffer, key, charArray);
        } else if (SshInputUtils.isDeleteKey(buffer)) {
            processLocalDeleteKey(buffer, charArray);
        } else if (SshInputUtils.isInsertKey(buffer)) {
            processLocalInsertKey(buffer, charArray);
        } else {
            processLocalNormalKey(buffer, key, charArray);
        }
    }

    private void processLocalNormalKey(byte[] buffer, String key, char[] charArray) throws IOException {
        log.info(key);
        if (!localMode) {
            return;
        }
        // 普通字符输入，回传，考虑插入模式的情况，覆盖还是追加
        if (key.length() > 0 && key.charAt(0) != SshInputUtils.ENTER_CHAR) {
            for (int i = 0; i < charArray.length; i++) {
                if (clientShellContext.insertMode) {
                    clientShellContext.currentCommandBuffer.add(clientShellContext.colIndex, charArray[i]);
                } else {
                    if (clientShellContext.colIndex < clientShellContext.currentCommandBuffer.size()) {
                        clientShellContext.currentCommandBuffer.set(clientShellContext.colIndex, charArray[i]);
                    } else {
                        clientShellContext.currentCommandBuffer.add(charArray[i]);
                    }
                }
                clientShellContext.colIndex++;
            }
        }
        if (SshInputUtils.isEnterChar(buffer)) {
            String command = new String(unboxed(clientShellContext.currentCommandBuffer));
            if (!clientShellContext.currentCommandBuffer.isEmpty()) {
                // 指令
                processLocalCommand(command);
            } else {
                // 换行
                key = key.concat(PWD);
                clientOut.write(key.getBytes(StandardCharsets.UTF_8));
                clientOut.flush();
            }
        } else {
            // 输入回显或转发
            clientOut.write(buffer);
            clientOut.flush();
        }
    }

    private void processLocalInsertKey(byte[] buffer, char[] charArray) throws IOException {
        if (localMode) {
            if (clientShellContext.insertMode) {
                clientOut.write(CMD_INSERT_MODE_FORBID);
            } else {
                clientOut.write(CMD_INSERT_MODE_OPEN);
            }
            clientOut.flush();
            clientShellContext.insertMode = !clientShellContext.insertMode;
        }
    }

    private void processLocalLeftRightKey(byte[] buffer, String key, char[] charArray) throws IOException {
        if (localMode) {
            this.clientShellContext.colIndex += isLeftKey(buffer) ? -1 : 1;
            if (this.clientShellContext.colIndex < 0) {
                this.clientShellContext.colIndex = 0;
            } else if (this.clientShellContext.colIndex > clientShellContext.currentCommandBuffer.size()) {
                this.clientShellContext.colIndex = clientShellContext.currentCommandBuffer.size();
            } else {
                clientOut.write(buffer);
                clientOut.flush();
            }
        }
    }

    private void processLocalDeleteKey(byte[] buffer, char[] charArray) throws IOException {

        if (localMode) {
            if (!clientShellContext.currentCommandBuffer.isEmpty()) {
                if (--clientShellContext.colIndex >= 0) {
                    clientShellContext.currentCommandBuffer.remove(clientShellContext.colIndex);
                    clientOut.write(CMD_DELETE);
                    clientOut.flush();
                }
                if (clientShellContext.colIndex < 0) {
                    clientShellContext.colIndex = 0;
                }
            }
        }
    }

    private void processLocalUpDownKey(byte[] raw, char[] charArray) throws IOException {

        // 首次进来的时候如果当前指令缓冲里边有数据，则进行缓存，否则不做处理
        if (!localMode) {
            return;
        }

        if (Objects.isNull(clientShellContext.bufferedCommand) && !clientShellContext.currentCommandBuffer.isEmpty()) {
            clientShellContext.bufferedCommand = clientShellContext.currentCommandBuffer;
        }
        clientShellContext.rowIndex += SshInputUtils.isDownKey(raw) ? 1 : -1;
        if (clientShellContext.rowIndex < 0) {
            clientShellContext.rowIndex = 0;
        }
        String command = "";
        if (clientShellContext.rowIndex >= clientShellContext.historyCommandList.size()) {
            clientShellContext.currentCommandBuffer.clear();
            clientShellContext.rowIndex = clientShellContext.historyCommandList.size(); // 始终保持方向上键按一下就是最近执行的历史记录
            if (Objects.nonNull(clientShellContext.bufferedCommand)) {
                command = new String(unboxed(clientShellContext.bufferedCommand));
                if (!clientShellContext.bufferedCommand.isEmpty()) { // 恢复
                    clientShellContext.currentCommandBuffer = clientShellContext.bufferedCommand;
                }
                clientShellContext.bufferedCommand = null;
            }
        } else {
            if (Objects.isNull(clientShellContext.bufferedCommand)) { // 标记缓冲指令为空字符串，避免二次进来由于选择导致缓存选择记录
                clientShellContext.bufferedCommand = new LinkedList<>();
            }
            clientShellContext.currentCommandBuffer.clear();
            command = clientShellContext.historyCommandList.get(clientShellContext.rowIndex);
            List<Character> commandChars = command.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
            clientShellContext.currentCommandBuffer.addAll(commandChars);
        }
        clientShellContext.colIndex = command.length();
        log.info("select command:{}", command);

        // 本地模式，需要客户端需要设置回显
        // 移动光标到行起始位置
        // 清除从光标到行位的内容
        // 重新回显选中行内容
        String echo = PATH_PLACE_HOLDER + command;
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(CMD_MOVE_CURSOR_TO_LINE_BEGIN).put(CMD_CLEAR_FROM_CURSOR_TO_LINE_END).put(echo.getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        byte[] cmd = new byte[buffer.limit()];
        buffer.get(cmd);
        writeAndFlush(clientOut, cmd);
    }

    private void processLocalCommand(String command) throws IOException {
        if (!localMode) {
            return;
        }
        Utils.writeAndFlush(clientOut, "\r\n".getBytes(StandardCharsets.UTF_8));
        // 指令解析:
        Function<Object, String> function = commandMap.get(command);
        if (Objects.nonNull(function)) {
            String result = function.apply(command);
            if (Objects.nonNull(result)) {
                result += PWD;
                writeAndFlush(clientOut, result.getBytes(StandardCharsets.UTF_8));
            }
            clearClientContextAndRecord(command);
        } else {
            String assetId = command;
            // 资产解析:
            Asset asset = AssetService.lookupAsset(assetId);
            if (Objects.isNull(asset)) {
                // 资产不存在
                String resp = "没有资产" + PWD;
                writeAndFlush(clientOut, resp.getBytes(StandardCharsets.UTF_8));
//                clearClientContextAndRecord(command);
            } else {
                new Thread(() -> {
                    // 停止接收输入
                    pending = true;
                    // 登录远程
                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();
                    // using the client for multiple sessions...
                    try (ClientSession session = client.connect(asset.getUsername(), asset.getAddress(), asset.getPort()).verify(10, TimeUnit.SECONDS).getSession()) {
                        session.addPasswordIdentity(asset.getPassword());
                        session.auth().verify(10, TimeUnit.SECONDS);

                        PtyChannelConfiguration pytConfig = new PtyChannelConfiguration();
                        Map<String, String> env = ((ChannelSession) ((ChannelOutputStream) clientOut).getChannel()
                                .getRemoteWindow()
                                .getChannel())
                                .getEnvironment()
                                .getEnv();
                        env.remove("USER");
                        //pid
                        try (ChannelShell channelShell = session.createShellChannel()) {
                            this.remoteChannelShell = channelShell;
                            channelShell.setPtyType(env.get("TERM"));
//                            channelShell.setEnv("LANG", "zh_CN.UTF-8"); // 导致右移复制
                            env.entrySet().forEach(entry -> {
                                channelShell.setEnv(entry.getKey(), entry.getValue()); // 导致删除右移
                            });
//                            channelShell.setPtyLines(270);
//                            channelShell.setPtyColumns(55);
                            channelShell.setRedirectErrorStream(true);
                            channelShell.open().verify(10000, TimeUnit.MILLISECONDS);
                            log.info("remote channel shell open.");

                            flushLoginLog(channelShell, clientOut);
                            String tty = getTtyName(channelShell);

                            remoteShellContext = new RemoteShellContext(tty, clientShellContext.user, asset);
                            remoteIn = channelShell.getInvertedIn();
                            remoteOut = channelShell.getInvertedOut();
                            localMode = false;
                            pending = false;

                            // 开启远程到客户端推流线程
                            new PumpTask(remoteChannelShell, remoteShellContext, remoteOut, clientOut).start();
                            // Wait (forever) for the channel to close - signalling shell exited
                            channelShell.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);

                            log.info("remote channel shell closed.");
                            localMode = true;
                            remoteShellContext = null;
                            String assets = AssetService.listAssets();
                            writeAndFlush(clientOut, assets.concat(PWD).getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        pending = false;
                        try {
                            if (remoteChannelShell != null) {
                                remoteChannelShell.close();
                            }
                            if (remoteChannelShell != null) {
                                remoteChannelShell.getSession().close();
                            }
                        } catch (IOException ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
                }).start();
            }
            clearClientContextAndRecord(command);
        }
    }

    private void flushLoginLog(ChannelShell channelShell, OutputStream clientOut) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        int loop = 0;
        for (long current = start; current < start + 100; ) {
            long s = System.currentTimeMillis();
            if (channelShell.getInvertedOut().available() > 0) {
                byte[] buffer = new byte[channelShell.getInvertedOut().available()];
                int len = channelShell.getInvertedOut().read(buffer);
                log.info("flush login log:{}", Arrays.toString(new String(buffer).toCharArray()));
                Utils.writeAndFlush(clientOut, buffer);
            } else {
                Thread.sleep(10);
            }
            current += (System.currentTimeMillis() - s);
            loop++;
        }
        log.info("remote channel shell flushed.loop:{}", loop);
    }


    private String getTtyName(ChannelShell channelShell) throws IOException {

        Utils.writeAndFlush(channelShell.getInvertedIn(), TERMINAL_TTY_CMD.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = readCommandResultWithTimeout(channelShell.getInvertedOut());
        String ttyResult = new String(bytes, StandardCharsets.UTF_8);
//        String ttyResult = channelShell.getClientSession().executeRemoteCommand(TERMINAL_TTY_CMD);
        log.info("tty result:{}", ttyResult);
        String tty = ttyResult.split("\r\n")[1];
        int i = tty.indexOf("pts");
        if (i < 0) {
            throw new IllegalStateException("");
        }
        return tty.substring(i);

//        String terminalPidCmd = TERMINAL_PID_CMD.replace("$tty", tty);
//        String pidResult = channelShell.getClientSession().executeRemoteCommand(terminalPidCmd);
//        byte[] echo = blockReadCommandResult(channelShell.getInvertedOut());
//        log.info("pid result:{}", new String(echo));
//        byte[] pidBytes = blockReadCommandResult(channelShell.getInvertedOut());
//        String pidResult = new String(pidBytes, StandardCharsets.UTF_8).trim();
//        log.info("pid result:{}", pidResult);
        // ignore echo
//        blockReadCommandResult(channelShell.getInvertedOut());
//        String pid = pidResult.split("\r\n")[1];
//        return pidResult;
    }

    private void loadHistory(ClientSession session) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String history = session.executeRemoteCommand("cat ~/.bash_history", out, StandardCharsets.UTF_8);
//        log.info("history:\n{}", history);
        String[] commands = history.split("\n");
        for (String command : commands) {
            clientShellContext.historyCommandList.add(command);
        }
        clientShellContext.rowIndex = clientShellContext.historyCommandList.size();
    }

    private void clearClientContextAndRecord(String command) {
        if (!clientShellContext.historyCommandList.contains(command)) {
//                log.info("add command '{}' to history list", command);
            clientShellContext.historyCommandList.add(command);
        } else {
            clientShellContext.historyCommandList.remove(command);
            clientShellContext.historyCommandList.add(command);
        }
        clientShellContext.rowIndex = clientShellContext.historyCommandList.size();
        clientShellContext.colIndex = 0;
        clientShellContext.currentCommandBuffer.clear();
        clientShellContext.bufferedCommand = null;
    }

    private void clearRemoteContextAndRecord() {
        remoteShellContext.colIndex = 0;
        remoteShellContext.currentCommandBuffer.clear();
    }


    private String exit(Object o) {
        try {
            byte[] exit = "exit".getBytes(StandardCharsets.UTF_8);
            shell.getInputStream().write(exit);
            shell.getSession().close(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private String clear(Object o) {
        try {
            clientOut.write(CMD_CLEAR);
            clientOut.write(PWD.getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
