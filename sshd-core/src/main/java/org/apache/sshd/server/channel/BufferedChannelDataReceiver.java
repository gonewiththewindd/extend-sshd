package org.apache.sshd.server.channel;

import org.apache.sshd.common.PropertyResolver;
import org.apache.sshd.common.channel.LocalWindow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class BufferedChannelDataReceiver extends PipeDataReceiver {

    public static final char ENTER_CHAR = 13;
    public static final char CTRL_C_CHAR = 3;
    public static final char DELETE_CHAR = 127;
    public static final char ESCAPE_CHAR = 27;
    public static final char[] UP_CHAR = {ESCAPE_CHAR, '[', 'A'};
    public static final char[] DOWN_CHAR = {ESCAPE_CHAR, '[', 'B'};
    public static final char[] LEFT_CHAR = {ESCAPE_CHAR, '[', 'D'};
    public static final char[] RIGHT_CHAR = {ESCAPE_CHAR, '[', 'C'};

    public static final byte[] ERASE_LINE = {ESCAPE_CHAR, '[', '2', 'K'};
    public static final byte[] STORE_CURSOR_POSITION = {ESCAPE_CHAR, '[', 's'};
    public static final byte[] RESTORE_CURSOR_POSITION = {ESCAPE_CHAR, '[', 'u'};

    private int selectIndex = 0;
    private List<String> historyCommandList;
    private List<String> currentCommandBuffer;

    private boolean storeCursorPosition = false;


    public BufferedChannelDataReceiver(PropertyResolver resolver, LocalWindow localWindow) {
        super(resolver, localWindow);
        this.historyCommandList = new ArrayList<>();
        this.currentCommandBuffer = new LinkedList<>();
    }

    @Override
    public synchronized int data(ChannelSession channel, byte[] buf, int start, int len) {
        try {
            String received = new String(Arrays.copyOfRange(buf, start, start + len), StandardCharsets.US_ASCII);
//        log.info("Received: {}", received);
            StringBuilder rebuildBuilder = new StringBuilder();
            for (int i = 0; i < received.length(); i++) {
                // 过滤删除键
                if (received.charAt(i) == DELETE_CHAR && !currentCommandBuffer.isEmpty()) {
                    currentCommandBuffer.remove(currentCommandBuffer.size() - 1);
                } else {
                    rebuildBuilder.append(received.charAt(i));
                }
            }

            // 方向符号 指令选择和光标移动
            String rebuild = rebuildBuilder.toString();
            if (rebuild.length() == 3) {
                char[] charArray = rebuild.toCharArray();
                if (isSelectChar(charArray)) {
                    if (selectIndex > 0 && selectIndex < historyCommandList.size() - 1) {
                        selectIndex += Arrays.equals(charArray, UP_CHAR) ? -1 : 1;
                    }
                    if (!historyCommandList.isEmpty()) {
                        rebuild = historyCommandList.get(selectIndex);
                    }
                    if (!currentCommandBuffer.isEmpty()) {
                        this.currentCommandBuffer.clear();
                        // 清空当前输入行
                        super.data(channel, ERASE_LINE, 0, ERASE_LINE.length);
                        // 移动光标到行起始位置
                        if (storeCursorPosition) {
                            super.data(channel, RESTORE_CURSOR_POSITION, 0, RESTORE_CURSOR_POSITION.length);
                            storeCursorPosition = false;
                        }
                    }
                }
                if (isCursorMoveChar(charArray)) {

                }
            }

            log.info("received:{}, rebuild:{}", received, rebuild);
            if (received.charAt(received.length() - 1) == ENTER_CHAR) {
                // 回车键监测，输入结束
                String command = currentCommandBuffer.stream().collect(Collectors.joining(""));
                log.info("command:{}", command);
                if (this.historyCommandList.contains(command)) {
                    // move to top
                    this.historyCommandList.remove(command);
                }
                if (!"".equalsIgnoreCase(command.trim()) && !isEnterChar(command)) {
                    this.historyCommandList.add(command);
                }
                selectIndex = historyCommandList.size();
                this.currentCommandBuffer.clear();
                storeCursorPosition = false;
            } else {
//                if (!storeCursorPosition) {
//                    // 保存行起始位置光标
//                    super.data(channel, STORE_CURSOR_POSITION, 0, STORE_CURSOR_POSITION.length);
//                    storeCursorPosition = true;
//                }
                this.currentCommandBuffer.addAll(Arrays.asList(rebuild.split("")));
            }

            byte[] bytes = rebuild.getBytes();
            super.data(channel, bytes, 0, bytes.length);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return 0;
    }

    private boolean isEnterChar(String command) {
        return command.length() == 1 && command.charAt(0) == ENTER_CHAR;
    }

    private boolean isCursorMoveChar(char[] c) {

        return false;
    }

    private boolean isSelectChar(char[] c) {
        return Arrays.equals(c, UP_CHAR) || Arrays.equals(c, DOWN_CHAR);
    }

    @Override
    public void close() throws IOException {
        this.currentCommandBuffer = null;
    }
}
