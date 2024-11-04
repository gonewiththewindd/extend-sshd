package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class HandshakeResponsePacket extends MysqlPacket {
    private long clientFlag;
    private long maxPacketLength;
    private short characterSet;
    private CharSequence filter;
    private CharSequence username;
    private long authResponseLength;
    private byte[] authResponse;
    private CharSequence database;
    private CharSequence clientPluginName;
    private Map<String, String> attr;
}
