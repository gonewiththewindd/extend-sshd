package org.apache.sshd.jp.service.impl;

import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.service.def.AuthService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class AuthServiceImpl implements AuthService {

    private Set<String> blackList = new HashSet<>() {{
        add("alter table");
        add("drop table");
        add("drop database");
        add("update");
    }};

    @Override
    public void auth() {

    }

    @Override
    public boolean verify(AssetOperation operation) {
        for (String black : blackList) {
            if(operation.getOpt().startsWith(black)) {
                return false;
            }
        }
        return true;
    }
}
