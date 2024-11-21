package org.apache.sshd.jp.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.jp.model.req.AssetMessage;
import org.apache.sshd.jp.model.req.OpenParam;
import org.apache.sshd.jp.service.def.AssetCommandService;
import org.apache.sshd.jp.service.def.WebsocketService;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.EncodeException;
import javax.websocket.Session;
import java.io.IOException;

@Slf4j
@Component
public class DefaultWebsocketServiceImpl implements WebsocketService {

    @Autowired
    private AssetCommandService assetCommandService;

    @Override
    public void onOpen(Session session, OpenParam param) {
        // 身份认证
        Asset asset = AssetService.lookupAsset(param.getAssetId());
        log.info("onOpen, session:{}, asset:{}", session, asset);
//        authService.auth();
    }

    @Override
    public void onMessage(Session session, AssetMessage message) {
        log.info("onMessage, session:{}, message:{}", session, message);
        Object result = assetCommandService.process(message);
        try {
            session.getBasicRemote().sendObject(result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (EncodeException e) {
            throw new RuntimeException(e);
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
