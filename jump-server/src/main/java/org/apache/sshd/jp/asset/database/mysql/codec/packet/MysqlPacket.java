package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlProtocolConstants;

@Data
@Accessors(chain = true)
public class MysqlPacket {
    protected long length;
    protected short seqId;
    protected short head;
    protected ByteBuf raw;

    public static ByteBuf initPacket(int payloadLength) {
        return Unpooled.buffer(MysqlProtocolConstants.PROTOCOL_LENGTH_LENGTH + MysqlProtocolConstants.SEQ_ID_LENGTH + payloadLength);
    }
}
