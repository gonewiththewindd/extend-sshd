package org.apache.sshd.jp.asset.database.mysql.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.jp.asset.database.mysql.codec.MysqlServerPacketDecoder;
import org.apache.sshd.jp.asset.database.mysql.handler.MysqlServerHandler;
import org.apache.sshd.jp.utils.ApplicationContextHolder;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;

@Slf4j
public class MysqlProxyServer {

    private int port;

    public MysqlProxyServer(int port) {
        this.port = port;
    }

    public void start() {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                    ByteOrder.LITTLE_ENDIAN,
                                    16 * 1024 * 1024,
                                    0,
                                    3,
                                    1,
                                    0,
                                    false
                            ));
                            ch.pipeline().addLast(new MysqlServerPacketDecoder());
                            MysqlServerHandler serverProxyHandler = ApplicationContextHolder.getBean(MysqlServerHandler.class);
                            ch.pipeline().addLast(serverProxyHandler);
                        }
                    });

            ChannelFuture f = serverBootstrap.bind(new InetSocketAddress(port)).sync();
            log.info("mysql proxy server started on port {}.", port);

            f.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
