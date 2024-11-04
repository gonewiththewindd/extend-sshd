package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class AuthMoreDataPacket extends MysqlPacket {
    private CharSequence authenticationMethodData;
}
