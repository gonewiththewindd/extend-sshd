package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class QueryPacket extends MysqlPacket {
    private long parameterCount;
    private long parameterSetCount;
    private boolean[] nullBitMap;
    private short newParamsBindFlag;
    private int paramTypeAndFlag;
    private CharSequence parameterName;
    private CharSequence sql;
}
