package org.apache.sshd.jp.websocket;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.jp.model.req.OpenParam;
import org.apache.sshd.jp.model.req.WsMessage;
import org.apache.sshd.jp.service.def.WebsocketService;
import org.apache.sshd.jp.utils.ApplicationContextHolder;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Component
@ServerEndpoint(value = "/websocket/asset/{assetId}")
public class AssetEndpoint {

    @OnOpen
    public void onOpen(Session session,
                       @PathParam("token") String token,
                       @PathParam("assetId") String assetId) {
        OpenParam openParam = new OpenParam()
                .setAssetId(assetId)
                .setToken(token);
        WebsocketService websocketService = ApplicationContextHolder.getBean(WebsocketService.class);
        websocketService.onOpen(session, openParam);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        WsMessage wsMessage = JSON.parseObject(message, WsMessage.class);
        WebsocketService websocketService = ApplicationContextHolder.getBean(WebsocketService.class);
        websocketService.onMessage(session, wsMessage);
    }

    @OnClose()
    public void onClose(Session session) {

    }

    @OnError()
    public void onError(Session session, Throwable throwable) {
        log.error("websocket error", throwable);
    }


    public static void main(String[] args) {
        byte[] bytes = "8.0.39".getBytes(StandardCharsets.UTF_8);
        byte[] bytes1 = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, bytes1, 0, bytes.length);
        bytes1[bytes1.length - 1] = 0x00;
        System.out.println(Arrays.toString(bytes1));
    }
}
