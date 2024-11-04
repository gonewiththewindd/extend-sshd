package org.apache.sshd.jp.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

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

}
