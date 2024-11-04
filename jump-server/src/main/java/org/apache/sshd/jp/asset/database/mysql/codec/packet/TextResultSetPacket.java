package org.apache.sshd.jp.asset.database.mysql.codec.packet;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class TextResultSetPacket extends MysqlPacket {

    private short metadataFollows;
    private long columnCount;
    private ColumnDefinition41 columnDefinition;
    private List<List<Object>> rowData;

    /**
     * Protocol::ColumnDefinition41:
     */
    @Data
    @Accessors(chain = true)
    public static class ColumnDefinition41 {
        private CharSequence catalog;
        private CharSequence schema;
        private CharSequence table;
        private CharSequence orgTable;
        private CharSequence name;
        private CharSequence orgName;
        private long fixedLengthFieldsLength;
        private int characterSet;
        private long columnLength;
        private short type;
        private int flags;
        private short decimal;
    }
}
