package org.apache.sshd.jp.asset.database.mysql.codec;

public interface PacketTypes {

    short OK = 0x00;
    short AUTH_MORE_DATA = 0x01;
    short HANDSHAKE_V9 = 0x09;
    short HANDSHAKE_V10 = 0x0a;
    short COM_QUERY = 0x03;
    short ERR = 0xff;
    short EOF = 0xfe;

    short FORWARD = 0x0ffd;
    short TEXT_RESULT_SET = 0x0ffe;
    short HANDSHAKE_RESP = 0x0fff;
}
