package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MysqlPacket {
    protected long length;
    protected short seqId;
    protected short head;
    protected ByteBuf raw;
}
