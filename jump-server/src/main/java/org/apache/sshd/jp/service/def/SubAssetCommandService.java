package org.apache.sshd.jp.service.def;

import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.AssetMessage;

/**
 * 资产命令
 */
public interface SubAssetCommandService<IN extends AssetMessage, OUT extends AssetOperation> {

    AssetOperation parse(IN message);

    boolean verify(AssetOperation operation);

    OUT execute(AssetOperation operation);
}
