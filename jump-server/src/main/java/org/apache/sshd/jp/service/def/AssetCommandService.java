package org.apache.sshd.jp.service.def;

import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.WsMessage;

/**
 * 资产命令
 */
public interface AssetCommandService {

    AssetOperation parse(WsMessage message);

    void execute(AssetOperation operation);
}
