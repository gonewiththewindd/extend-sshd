package org.apache.sshd.jp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.sshd.jp.constants.AssetTypeConstants;
import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.AssetMessage;
import org.apache.sshd.jp.service.def.AssetCommandService;
import org.apache.sshd.jp.service.def.AuthService;
import org.apache.sshd.jp.service.def.SubAssetCommandService;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class AssetCommandServiceImpl implements AssetCommandService {

    @Autowired
    private AuthService authService;
    @Autowired
    private Map<String, SubAssetCommandService> assetServiceMap;


    @Override
    public Object process(AssetMessage message) {

        Asset asset = AssetService.lookupAsset(message.getAssetId());
        String assetServiceName = asset.getSubAssetType().concat(AssetTypeConstants.SERVICE);
        SubAssetCommandService assetService = assetServiceMap.get(assetServiceName);
        if (Objects.isNull(assetService)) {
            log.info("asset service '{}' not exist", assetServiceName);
            return null;
        }
        // 操作解析
        AssetOperation operation = assetService.parse(message);
        operation.setAssetType(asset.getAssetType());
        operation.setSubAssetType(asset.getSubAssetType());
        operation.setAsset(asset);
        // 操作权限校验
        boolean verify = authService.verify(operation);
        if (verify) {
            // 操作转发执行
            assetService.execute(operation);
            // 操作记录
//            assetOperationService.save(operation);
            // 操作结果返回
            return operation.getResult();
        } else {
            return "此命令禁止被执行";
        }
    }

    public static void main(String[] args) throws IOException {

        String suffix = ".encrypt";
        String srcFilename = "微信截图_20241018140610.png";
        String dir = "D:\\Users\\Pictures";
        String srcFilePath = dir.concat(File.separator).concat(srcFilename);
        Path tempFile = Files.createTempFile(Paths.get(dir), srcFilename, suffix);
        try (InputStream in = Files.newInputStream(Paths.get(srcFilePath));
             OutputStream out = Files.newOutputStream(tempFile)) {
            byte[] bytes = in.readAllBytes();
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] < 0) {
                    bytes[i] = (byte) (bytes[i] + 128);
                } else {
                    bytes[i] = (byte) (bytes[i] - 128);
                }
            }
            out.write(bytes);
            out.flush();

            String extension = "." + FilenameUtils.getExtension(srcFilename);
            try (InputStream ein = Files.newInputStream(tempFile);
                 OutputStream dout = Files.newOutputStream(Files.createTempFile(Paths.get(dir), UUID.randomUUID().toString(), extension))) {
                byte[] dbytes = ein.readAllBytes();
                for (int i = 0; i < dbytes.length; i++) {
                    if (dbytes[i] < 0) {
                        dbytes[i] = (byte) (dbytes[i] + 128);
                    } else {
                        dbytes[i] = (byte) (dbytes[i] - 128);
                    }
                }
                dout.write(dbytes);
                dout.flush();
            }
        }
    }
}
