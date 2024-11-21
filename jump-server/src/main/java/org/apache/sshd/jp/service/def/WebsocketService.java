package org.apache.sshd.jp.service.def;

import org.apache.sshd.jp.model.req.OpenParam;
import org.apache.sshd.jp.model.req.AssetMessage;

import javax.websocket.Session;

public interface WebsocketService {

    void onOpen(Session session, OpenParam param);

    void onMessage(Session session, AssetMessage message);

    void onClose(Session session);

    void onError(Session session, Throwable throwable);
}
