package org.apache.sshd.server.shell.test;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Asset {
    private String id;
    private String name;
    private String address;
    private Integer port;
    private String platform;
    private String group;
    private String remark;

    private String assetType;
    private String subAssetType;

    private String username;
    private String password;
}

