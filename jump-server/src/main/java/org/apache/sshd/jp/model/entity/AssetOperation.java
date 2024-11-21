package org.apache.sshd.jp.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.sshd.jp.model.req.AssetMessage;
import org.apache.sshd.server.shell.test.Asset;

@Data
@Accessors(chain = true)
//@TableName()
public class AssetOperation {

    @TableId
    private Long id;
    private String assetId;
    private String assetType;
    private String subAssetType;
    private String opt;
    private String result;

    private transient AssetMessage assetMessage;
    private transient Asset asset;

}
