package org.apache.sshd.jp.asset;

import org.apache.sshd.jp.model.Asset;

import java.util.HashMap;
import java.util.Map;

public class AssetService {

    public static final Map<String, Asset> assets = new HashMap<>() {{

        Asset asset1 = new Asset()
                .setId("1")
                .setName("centos7")
                .setAddress("192.168.71.101")
                .setPlatform("Linux")
                .setGroup("DEFAULT")
                .setRemark("");
        Asset asset2 = new Asset()
                .setId("2")
                .setName("windows10")
                .setAddress("192.168.71.1")
                .setPlatform("Windows")
                .setGroup("DEFAULT")
                .setRemark("");
        put(asset1.getId(), asset1);
        put(asset2.getId(), asset2);
    }};

    public static Asset lookupAsset(String id) {
        return assets.get(id);
    }


}
