package org.apache.sshd.jp.asset.database.mysql.model;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.server.shell.test.Asset;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Data
@Accessors(chain = true)
public class MysqlChannelContext {

    private byte[] authPluginData;
    private String authPluginName;
    private ChannelId channelId;

    private String storedPassword = "123321";

    private long capabilityFlag;
    private long maxPacketLength = 16 * 1024 * 1024;
    private short characterSet;
    private Map<String, String> attr;

    private Asset asset;

    private Channel clientChannel;
    private Channel proxiedChannel;

    private volatile boolean authenticated;

    private Lock lock = new ReentrantLock();
    public Condition proxiedChannelAuthenticateFinished = lock.newCondition();

    public MysqlChannelContext() {
    }

    public void sendHandshakePacket() {

    }

    public static void main(String[] args) throws IOException {

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 3306));
        while (!socket.isClosed()) {
            if (socket.getInputStream().available() > 0) {
                byte[] bytes = IOUtils.readFully(socket.getInputStream(), socket.getInputStream().available());
                log.info("Received packet:{}", new String(bytes, StandardCharsets.UTF_8));
            }
        }


    }
}
