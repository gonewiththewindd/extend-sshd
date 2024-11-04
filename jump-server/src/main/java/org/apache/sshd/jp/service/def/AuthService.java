package org.apache.sshd.jp.service.def;

import org.apache.sshd.jp.model.entity.AssetOperation;

public interface AuthService {

    void auth();

    boolean verify(AssetOperation operation);
}
