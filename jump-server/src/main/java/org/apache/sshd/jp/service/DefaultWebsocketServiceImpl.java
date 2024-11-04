package org.apache.sshd.jp.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.jp.constants.AssetTypeConstants;
import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.OpenParam;
import org.apache.sshd.jp.model.req.WsMessage;
import org.apache.sshd.jp.service.def.AssetCommandService;
import org.apache.sshd.jp.service.def.AssetOperationService;
import org.apache.sshd.jp.service.def.AuthService;
import org.apache.sshd.jp.service.def.WebsocketService;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class DefaultWebsocketServiceImpl implements WebsocketService {

    @Autowired
    private AuthService authService;
    @Autowired
    private Map<String, AssetCommandService> assetServiceMap;
    @Autowired
    private AssetOperationService assetOperationService;

    @Override
    public void onOpen(Session session, OpenParam param) {
        // 身份认证
        Asset asset = AssetService.lookupAsset(param.getAssetId());
        log.info("onOpen, session:{}, asset:{}", session, asset);
        authService.auth();
    }

    @Override
    public void onMessage(Session session, WsMessage message) {
        log.info("onMessage, session:{}, message:{}", session, message);
        Asset asset = AssetService.lookupAsset(message.getAssetId());
        String assetServiceName = asset.getSubAssetType().concat(AssetTypeConstants.SERVICE);
        AssetCommandService assetService = assetServiceMap.get(assetServiceName);
        if (Objects.isNull(assetService)) {
            log.info("asset service '{}' not exist", assetServiceName);
            return;
        }
        // 操作解析
        AssetOperation operation = assetService.parse(message);
        operation.setAssetType(asset.getAssetType());
        operation.setSubAssetType(asset.getSubAssetType());
        // 操作权限校验
        boolean verify = authService.verify(operation);
        if (verify) {
            // 操作转发执行
            assetService.execute(operation);
            // 操作记录
//            assetOperationService.save(operation);
            // 操作结果返回
            session.getAsyncRemote().sendText(operation.getResult());
        } else {
            session.getAsyncRemote().sendText("此命令禁止被执行");
        }
    }

    @Override
    public void onClose(Session session) {
        log.info("onClose, session:{}", session);
    }

    @Override
    public void onError(Session session, Throwable throwable) {
        log.info("onError, session:{}", session, throwable);
    }
}
