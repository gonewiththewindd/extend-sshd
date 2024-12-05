package org.apache.sshd.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class SshInputUtils {

    public static final char ENTER_CHAR = 13;
    public static final char CTRL_C_CHAR = 3;
    public static final char CTRL_X_CHAR = 24;
    public static final byte BEL_CHAR = 7;
    public static final byte BACKSPACE_CHAR = 8;
    public static final byte TAB_CHAR = 9;
    public static final byte DELETE_CHAR = 127;
    public static final byte[] SPACE_CHAR = {32};
    public static final byte ESCAPE_CHAR = 27;

    public static final byte[] UP_CHAR = {ESCAPE_CHAR, '[', 'A'};
    public static final byte[] DOWN_CHAR = {ESCAPE_CHAR, '[', 'B'};
    public static final byte[] LEFT_CHAR = {ESCAPE_CHAR, '[', 'D'};
    public static final byte[] RIGHT_CHAR = {ESCAPE_CHAR, '[', 'C'};

    public static final byte[] INSERT_CHAR = {ESCAPE_CHAR, '[', '2', '~'};

    public static final byte[] ERASE_LINE = {ESCAPE_CHAR, '[', '2', 'K'};
    public static final byte[] STORE_CURSOR_POSITION = {ESCAPE_CHAR, '[', 's'};
    public static final byte[] RESTORE_CURSOR_POSITION = {ESCAPE_CHAR, '[', 'u'};

    public static final byte[] CMD_CLEAR = {ESCAPE_CHAR, '[', 'H', ESCAPE_CHAR, '[', '2', 'J'};
    public static final byte[] CMD_MOVE_CURSOR_TO_LINE_BEGIN = {'\r'};
    public static final byte[] CMD_MOVE_CURSOR_TO_COLUMN = {ESCAPE_CHAR, '[', 10, 'G'};
    public static final byte[] CMD_CLEAR_FROM_CURSOR_TO_LINE_END = {ESCAPE_CHAR, '[', 'K'};
    public static final byte[] CMD_DELETE = {ESCAPE_CHAR, '[', 'D', ESCAPE_CHAR, '[', 'P'};

    public static final byte[] CMD_INSERT_MODE_OPEN = {ESCAPE_CHAR, '[', '2', 'h', ESCAPE_CHAR, '[', '4', 'h'};
    public static final byte[] CMD_INSERT_MODE_FORBID = {ESCAPE_CHAR, '[', '2', 'l', ESCAPE_CHAR, '[', '4', 'l'};

    public static final String PATH_PLACE_HOLDER = "[Host]>";
    public static final String PWD = "\r\n" + PATH_PLACE_HOLDER;

    public static final String EMPTY_STRING = "";

    // 备用缓冲区
    public static final byte[] ALTERNATE_BUFFER_SWITCHING = {ESCAPE_CHAR, '[', '?', '1', '0', '4', '9', 'h'};
    public static final byte[] ALTERNATE_BUFFER_EXIT = {ESCAPE_CHAR, '[', '?', '1', '0', '4', '9', 'l'};
    // 光标显示
    public static final byte[] CURSOR_ENABLE_MODE_SHOW = {ESCAPE_CHAR, '[', '?', '2', '5', 'h'};
    public static final byte[] CURSOR_ENABLE_MODE_HIDE = {ESCAPE_CHAR, '[', '?', '2', '5', 'l'};

    public static final char EDIT_MODE_INSERT_CHAR = 'i';
    public static final char EDIT_MODE_COMMAND_CHAR = ':';
    public static final char[] EDIT_MODE_EXIT_Q = {'q'};

    public static final byte[] EDIT_MODE_EXIT_Q_SUCCEED = {'\r', ESCAPE_CHAR, '[', '?', '2', '5', 'l'};

    public static final char[] EDIT_MODE_EXIT_FORCE_Q = {'q', '!'};
    public static final char[] EDIT_MODE_EXIT_WQ = {'w', 'q'};
    public static final char[] EDIT_MODE_EXIT_FORCE_WQ = {'w', 'q', '!'};

    public static final char y = 'y';
    public static final char Y = 'Y';
    public static final char n = 'n';
    public static final char N = 'N';
    public static final Set<Character> CONFIRM = new HashSet<>(Arrays.asList(y, n, Y, N));

    public static boolean isEnterChar(byte[] c) {
        return c[0] == ENTER_CHAR;
    }

    public static boolean isCtrlC(byte[] c) {
        return c.length == 1 && c[0] == CTRL_C_CHAR;
    }

    public static boolean isQ(byte[] c) {
        return c[0] == 'q';
    }

    public static boolean isCursorMoveChar(char[] c) {

        return false;
    }

    public static boolean isUpDownKey(byte[] c) {
        return isUpKey(c) || isDownKey(c);
    }

    public static boolean isUpKey(byte[] c) {
        return Arrays.equals(c, UP_CHAR);
    }

    public static boolean isDownKey(byte[] c) {
        return Arrays.equals(c, DOWN_CHAR);
    }

    public static byte[] moveCursorToColumn(byte column) {
        byte[] cmd = CMD_MOVE_CURSOR_TO_COLUMN;
        cmd[2] = column;
        return cmd;
    }

    public static boolean isDeleteKey(byte[] charArray) {
        return charArray.length == 1 && (charArray[0] == BACKSPACE_CHAR || charArray[0] == DELETE_CHAR);
    }

    public static boolean isEscapeKey(byte[] charArray) {
        return charArray.length == 1 && charArray[0] == ESCAPE_CHAR;
    }

    public static boolean isLeftRightKey(byte[] charArray) {
        return isLeftKey(charArray) || isRightKey(charArray);
    }

    public static boolean isLeftKey(byte[] charArray) {
        return Arrays.equals(LEFT_CHAR, charArray);
    }

    public static boolean isRightKey(byte[] charArray) {
        return Arrays.equals(RIGHT_CHAR, charArray);
    }

    public static boolean isInsertKey(byte[] charArray) {
        return Arrays.equals(INSERT_CHAR, charArray);
    }

    public static boolean isH(byte[] input) {
        return input.length == 1 && input[0] == 'h';
    }

    public static boolean isQuestionMask(byte[] input) {
        return input.length == 1 && input[0] == '?';
    }

    public static boolean enterTextCommandMod(byte[] transfer, int offset, int len) {
        // 发生备用缓冲区切换
        if (matchPattern(transfer, offset, len, ALTERNATE_BUFFER_SWITCHING) > 0) {
            return true;
        }
        // 发生光标显示切换
        if (matchPattern(transfer, offset, len, CURSOR_ENABLE_MODE_HIDE) > 0) {
            return true;
        }
        return false;
    }

    public static boolean exitTextCommandMod(byte[] transfer, int offset, int len) {
        // 发生备用缓冲区切换
        if (matchPattern(transfer, offset, len, ALTERNATE_BUFFER_EXIT) > 0) {
            return true;
        }
        // 发生光标显示切换
        if (matchPattern(transfer, offset, len, CURSOR_ENABLE_MODE_HIDE) > 0) {
            return true;
        }
        return false;
    }

    public static int matchPattern(byte[] content, int offset, int len, byte[] pattern) {
        int pl = pattern.length;
        for (; offset < len - pl; ) {
            for (int i = offset, j = 0; j < pl; i++) {
                if (content[i] == pattern[j]) {
                    j++;
                    if (j == pl) {
                        log.info("match pattern '{}' at offset:{}", Arrays.toString(pattern), offset);
                        return offset;
                    }
                } else {
                    offset += j;
                    break;
                }
            }
        }
        return -1;
    }

    public static boolean isTabKey(char[] charArray) {
        return charArray.length == 1 && charArray[0] == TAB_CHAR;
    }
}
