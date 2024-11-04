package org.apache.sshd.server.shell.test;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.sshd.server.shell.InvertedShellWrapper.PWD;

public class AssetService {

    public static final Map<String, Asset> assets = new HashMap<>() {{

        Asset linux = new Asset()
                .setId("1")
                .setName("centos7")
                .setAddress("192.168.71.101")
                .setPort(22)
                .setPlatform("Linux")
                .setGroup("DEFAULT")
                .setUsername("root")
                .setPassword("12121122")
                .setAssetType("host")
                .setSubAssetType("linux")
                .setRemark("");
        Asset windows = new Asset()
                .setId("2")
                .setName("windows10")
                .setAddress("192.168.71.1")
                .setPort(22)
                .setPlatform("Windows")
                .setGroup("DEFAULT")
                .setUsername("Administrator")
                .setPassword(".12121122.")
                .setAssetType("host")
                .setSubAssetType("windows")
                .setRemark("");
        Asset dm = new Asset()
                .setId("3")
                .setName("dm")
                .setAddress("192.168.71.1")
                .setPort(52369)
                .setPlatform("Windows")
                .setGroup("DEFAULT")
                .setUsername("SYSDBA")
                .setPassword("SYSDBA")
                .setAssetType("database")
                .setSubAssetType("dm")
                .setRemark("");

        Asset mysql = new Asset()
                .setId("4")
                .setName("mysql8")
                .setAddress("192.168.71.1")
                .setPort(3306)
                .setPlatform("Windows")
                .setGroup("DEFAULT")
                .setUsername("root")
                .setPassword("12121122.")
                .setAssetType("database")
                .setSubAssetType("mysql")
                .setRemark("");

        put(linux.getId(), linux);
        put(windows.getId(), windows);
        put(dm.getId(), dm);
        put(mysql.getId(), mysql);
    }};

    public static Asset lookupAsset(String id) {
        return assets.get(id);
    }

    public static String listAssets() {
        String header = String.format("%-20s|%-20s|%-20s|%-20s|%-20s\r\n", "ID", "名称", "地址", "平台", "组织", "备注");
        String line = String.format("%-20s+%-20s+%-20s+%-20s+%-20s\r\n", "", "", "", "", "", "", "").replace(" ", "-");
//        List<Asset> assets = AssetService.listAssets();
        StringBuilder content = new StringBuilder();
        for (Asset asset : assets.values()) {
            String assetFormat = String.format("%-20s|%-20s|%-20s|%-20s|%-20s\r\n",
                    asset.getId(), asset.getName(), asset.getAddress(), asset.getPlatform(), asset.getGroup(), asset.getRemark());
            content.append(assetFormat);
        }
        return header + line + content + PWD;
    }


}
