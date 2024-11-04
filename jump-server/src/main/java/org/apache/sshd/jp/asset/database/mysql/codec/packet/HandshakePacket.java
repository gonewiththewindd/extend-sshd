package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class HandshakePacket extends MysqlPacket {

    private short protocolVersion;
    private CharSequence serverVersion;
    private long threadId;
    private short filter;
    private long capabilityFlags;
    private short characterSet;
    private int statusFlag;
    private CharSequence reserved;

    private short authPluginDataLength;
    private CharSequence authPluginName;
    private byte[] authPluginData;
}
