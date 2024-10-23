package org.gone.sshd;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.shell.InteractiveProcessShellFactory;
import org.gone.sshd.cmd.DefaultCommandFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootApplication
public class ExtendSshdApplication {
    public static void main(String[] args) {

        System.setProperty("file.encoding", StandardCharsets.UTF_8.name());

        ConfigurableApplicationContext ctx = SpringApplication.run(ExtendSshdApplication.class);
        try {

            SshServer sshd = SshServer.setUpDefaultServer();
            sshd.setPort(2222);
            sshd.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, TimeUnit.MINUTES, 1);
            sshd.setPasswordAuthenticator(ctx.getBean(PasswordAuthenticator.class));
            // 设置秘钥对路径
            sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("C:\\Users\\Administrator.SKY-20240912AHP\\.ssh\\id_rsa")));
            // 设置交互性命令行工具
            sshd.setShellFactory(InteractiveProcessShellFactory.INSTANCE);
//            sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
            sshd.setCommandFactory(new DefaultCommandFactory());

            sshd.addSessionListener(new SessionListener() {
                @Override
                public void sessionEvent(Session session, Event event) {
                    switch (event) {
                        case KeyEstablished -> {
                        }
                        case Authenticated -> {
                            session.addChannelListener(new ChannelListener() {
                                @Override
                                public void channelStateChanged(Channel channel, String hint) {
                                    log.info("channelStateChanged: {}", hint);
                                }
                            });
                            /*String assets = "ID | 名称            | 地址           | 平台                                            | 组织                                            | 备注                                           \n" +
                                    "-----+-----------------+----------------+-------------------------------------------------+-------------------------------------------------+------------------------------------------------\n" +
                                    "  1  | centos7          | 192.168.71.101 | Linux                                           | DEFAULT                                         |                                                \n" +
                                    "  2  | local_windows10 | 192.168.71.1   | Windows                                         | DEFAULT                                         |                                        ";
                            byte[] bytes = assets.getBytes(StandardCharsets.UTF_8);
                            Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_CHANNEL_DATA, 0);
                            buffer.putBytes(bytes);
//                            // 会话创建，发送用户资产列表
                            try {
                                session.writePacket(buffer);
                            } catch (IOException e) {
                                log.error(e.getMessage(), e);
                                throw new RuntimeException(e);
                            }*/
                        }
                        case KexCompleted -> {
                        }
                    }
                }
            });

            sshd.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}