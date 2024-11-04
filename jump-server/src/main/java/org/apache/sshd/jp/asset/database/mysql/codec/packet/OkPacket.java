package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class OkPacket extends MysqlPacket {
    private long affectRows;
    private long lastInsertId;
    private int statusFlags;
    private int warnings;
    private CharSequence info;
    private CharSequence sessionStateInfo;
}
