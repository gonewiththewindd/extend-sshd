package org.apache.sshd.jp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.jp.asset.database.mysql.common.CapabilityFlags;
import org.apache.sshd.jp.asset.database.mysql.server.MysqlProxyServer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootApplication(/*scanBasePackages = {"org.apache.sshd.jp", "org.apache.sshd.jp.websocket"}*/)
public class ExtendSshdApplication {
    public static void main(String[] args) {

        ConfigurableApplicationContext ctx = SpringApplication.run(ExtendSshdApplication.class, args);
        // 主机类ssh
        try {
            SshServer sshd = SshServer.setUpDefaultServer();
            sshd.setPort(2222);
            sshd.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, TimeUnit.MINUTES, 10);
            sshd.setPasswordAuthenticator(ctx.getBean(PasswordAuthenticator.class));
            // 设置秘钥对路径
            sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("C:\\Users\\Administrator.SKY-20240912AHP\\.ssh\\id_rsa")));
            // 设置交互性命令行工具
            sshd.setShellFactory(new ProcessShellFactory("cmd", "cmd", "/u"));
            //            sshd.setShellFactory(InteractiveProcessShellFactory.INSTANCE);
            sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
            //            sshd.setCommandFactory(new DefaultCommandFactory());
            sshd.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 客户端数据库
        new MysqlProxyServer(33069).start();
    }

    public static byte[] append0x00(byte[] bytes) {
        byte[] appended = Arrays.copyOf(bytes, bytes.length + 1);
        appended[appended.length - 1] = 0x00;
        return appended;
    }
}
