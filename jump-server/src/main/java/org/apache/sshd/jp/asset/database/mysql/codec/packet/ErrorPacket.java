package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class ErrorPacket extends MysqlPacket {
    private int errorCode;
    private CharSequence sqlStateMarker;
    private CharSequence sqlState;
    private CharSequence errorMessage;
}
