package org.apache.sshd.jp.service.def;

import org.apache.sshd.jp.model.req.AssetMessage;

public interface AssetCommandService {

    Object process(AssetMessage msg);

}
