package org.apache.sshd.jp.asset.database.mysql.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.jp.asset.database.mysql.codec.MysqlServerPacketDecoder;
import org.apache.sshd.jp.asset.database.mysql.codec.PacketTypes;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.HandshakeResponsePacket;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.MysqlPacket;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.QueryPacket;
import org.apache.sshd.jp.asset.database.mysql.common.CapabilityFlags;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlPasswordAlgorithmEnums;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlProtocolConstants;
import org.apache.sshd.jp.asset.database.mysql.model.MysqlChannelContext;
import org.apache.sshd.jp.asset.database.mysql.utils.AuthUtils;
import org.apache.sshd.jp.utils.ApplicationContextHolder;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ChannelHandler.Sharable
@Component
public class MysqlServerHandler extends SimpleChannelInboundHandler<MysqlPacket> {

    public Map<String, MysqlChannelContext> channelContextMap = new ConcurrentHashMap<>();
//    public Map<String, String> proxiedChannelMap = new ConcurrentHashMap<>();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log.info("channelRegistered");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.info("channelUnregistered");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]channel active, prepare to send handshake packet...");
        MysqlChannelContext channelContext = channelContextMap.get(ctx.channel().id().asLongText());
        if (Objects.isNull(channelContext) || !channelContext.isAuthenticated()) {
            sendHandshakePacket(ctx);
        }
    }

    private void sendHandshakePacket(ChannelHandlerContext ctx) {
        /*
            报文格式：
                payload_length | sequence_id | payload
         */
        // 发送握手报文，详细报文格式 https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_connection_phase_packets_protocol_handshake_v10.html
        int authPluginDataLen = 21;
//        byte[] authPluginData = RandomUtils.nextBytes(authPluginDataLen);
        byte[] authPluginData = new byte[]{-120, -126, 26, -76, -97, -27, -92, -84, 39, -40, -23, 78, 119, -54, 41, -112, 50, 36, -120, -15, 0};
        authPluginData[authPluginDataLen - 1] = 0x00;

        long ca = CapabilityFlags.toLong(CapabilityFlags.getImplicitCapabilities());

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(10); // protocol version
        buf.writeCharSequence("8.0.39", StandardCharsets.UTF_8); // server version
        buf.writeZero(1);
        buf.writeIntLE(RandomUtils.nextInt()); // thread id
        buf.writeBytes(Arrays.copyOfRange(authPluginData, 0, 8)); // auth-plugin-data-part-1
        buf.writeZero(1); // filler
        buf.writeShortLE((int) ca); // capability_flags_1
        buf.writeByte(83); // character_set::utf8mb4_general_ci=45
        buf.writeShortLE(2); // status_flags::SERVER_STATUS_AUTOCOMMIT=2
        buf.writeShortLE((int) (ca >> Short.SIZE)); // capability_flags_2
        buf.writeByte(authPluginDataLen);//auth_plugin_data_len
        buf.writeZero(10);//reserved. All 0s.
        buf.writeBytes(Arrays.copyOfRange(authPluginData, 8, authPluginDataLen)); // 	auth-plugin-data-part-2
        String authPluginName = MysqlPasswordAlgorithmEnums.caching_sha2_password.name();
        buf.writeCharSequence(authPluginName, StandardCharsets.UTF_8); // auth_plugin_name
//        buf.writeCharSequence("caching_sha2_password", StandardCharsets.UTF_8); // auth_plugin_name
        buf.writeZero(1);

        int len = buf.readableBytes();
        byte seqId = 0;
        ByteBuf packet = Unpooled.buffer(Integer.BYTES + len);
        packet.writeMediumLE(len); // payload length
        packet.writeByte(seqId); // sequence id
        packet.writeBytes(buf, 0, buf.readableBytes()); // payload
//        byte[] official = {74, 0, 0, 0, 10, 56, 46, 48, 46, 51, 57, 0, 23, 0, 0, 0, 37, 73, 13, 59, 55, 107, 62, 63, 0, -1, -1, -1, 2, 0, -1, -33, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 110, 105, 122, 14, 90, 81, 8, 86, 22, 121, 124, 21, 0, 99, 97, 99, 104, 105, 110, 103, 95, 115, 104, 97, 50, 95, 112, 97, 115, 115, 119, 111, 114, 100, 0};
//        byte[] mockssss = {74, 0, 0, 0, 10, 56, 46, 48, 46, 51, 57, 0, 50, 0, 0, 0, 105, 17, 90, 45, 67, 22, 5, 26, 0, -1, -1, -1, 2, 0, -1, -33, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 29, 47, 1, 98, 23, 106, 92, 106, 108, 59, 105, 125, 0, 99, 97, 99, 104, 105, 110, 103, 95, 115, 104, 97, 50, 95, 112, 97, 115, 115, 119, 111, 114, 100, 0};

        MysqlChannelContext channel = new MysqlChannelContext().setClientChannel(ctx.channel()).setChannelId(ctx.channel().id()).setAuthPluginData(authPluginData).setAuthPluginName(authPluginName);
        channelContextMap.putIfAbsent(channel.getChannelId().asLongText(), channel);

        log.info("[Server]send handshake packet:{}", packet);
        ctx.writeAndFlush(packet);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]channelInactive");

    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, MysqlPacket packet) throws Exception {

        short head = packet.getHead();
        switch (head) {
            case PacketTypes.HANDSHAKE_RESP -> {
                processHandshakeResponse((HandshakeResponsePacket) packet, ctx);
            }
            case PacketTypes.COM_QUERY -> {
                processQueryCommand((QueryPacket) packet, ctx);
            }
            default -> {
                forwardToProxiedChannel(packet, ctx);
            }
        }
    }

    private void processQueryCommand(QueryPacket packet, ChannelHandlerContext ctx) {
        // 转发指令
        MysqlChannelContext channelContext = channelContextMap.get(ctx.channel().id().asLongText());
        log.info("[Server]COMMAND, forward command to proxied channel '{}'..SQL:{}", channelContext.getProxiedChannel().remoteAddress(), packet.getSql());
        channelContext.getProxiedChannel().writeAndFlush(packet.getRaw());
    }

    private void forwardToProxiedChannel(MysqlPacket packet, ChannelHandlerContext ctx) {
        // 转发指令
        MysqlChannelContext channelContext = channelContextMap.get(ctx.channel().id().asLongText());
        log.info("[Server]forward packet to proxied channel '{}'...", channelContext.getProxiedChannel().remoteAddress());
        channelContext.getProxiedChannel().writeAndFlush(packet.getRaw());
    }

    private void processHandshakeResponse(HandshakeResponsePacket packet, ChannelHandlerContext ctx) {

        MysqlChannelContext channel = channelContextMap.get(ctx.channel().id().asLongText());
        // 更新客户端信息，用于后续和代理数据库协商
        channel.setCapabilityFlag(packet.getClientFlag());
        channel.setCharacterSet(packet.getCharacterSet());
        channel.setMaxPacketLength(Math.min(packet.getMaxPacketLength(), channel.getMaxPacketLength()));
        channel.setAttr(packet.getAttr());

        String authPluginName = channel.getAuthPluginName();
        if (!StringUtils.equalsIgnoreCase(authPluginName, packet.getClientPluginName())) {
            // 认证方式协商

        } else {
            if (ArrayUtils.isNotEmpty(packet.getAuthResponse())) {
                boolean verify = verifyPassword(channel, packet);
                if (verify) {
                    initRemoteConnection(packet, channel);
                } else {
                    sendErrorPacket(channel);
                }
            } else {
                // 通知客户端发送认证密码
                sendMoreAuthDataPacket();
            }
        }
    }

    private void initRemoteConnection(HandshakeResponsePacket packet, MysqlChannelContext context) {
        String serverHost = packet.getAttr().get("_server_host");
        // TODO 域名映射远程目标数据库，这个可以在认证通过处设置
        Asset asset = mappingDestDatabase(serverHost);
        log.info("[Server]bastion server authentication succeed, prepare to connect remote asset '{}'", asset);
        context.setAsset(asset);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(new NioEventLoopGroup());
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.handler(new ChannelInitializer<>() {
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
                    ch.pipeline().addLast(ApplicationContextHolder.getBean(MysqlClientHandler.class));
                }
            });
            ChannelFuture proxiedChannelFuture = bootstrap.connect(asset.getAddress(), asset.getPort());
            proxiedChannelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("[Server]connect to remote asset(database) '{}' success", asset);
                    Channel proxiedChannel = future.channel();
                    context.setProxiedChannel(proxiedChannel);
                    channelContextMap.put(proxiedChannel.id().asLongText(), context);
                } else {
                    throw new RuntimeException("Failed to connect to server");
                }
            });
            /*try {
                channel.getLock().lock();
                if (!channel.proxiedChannelAuthenticateFinished.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to connect to server:" + asset);
                }
            } finally {
                channel.getLock().unlock();
            }*/
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Asset mappingDestDatabase(String serverHost) {
        return AssetService.lookupAsset("4");
    }

    private void sendMoreAuthDataPacket() {
        log.info("[Server]send more auth data packet...");
    }

    private void sendErrorPacket(MysqlChannelContext channel) {
        log.info("[Server]send err packet...");
    }

    private void sendOkPacket(MysqlChannelContext channel, long clientFlag) {
        // ok packet
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0x00); // head
        buffer.writeByte(0); // affected rows
        buffer.writeByte(0); // last insert id

        int clientProtocolMask = 1 << CapabilityFlags.CLIENT_PROTOCOL_41.ordinal();
        int clientTransactionsMask = 1 << CapabilityFlags.CLIENT_TRANSACTIONS.ordinal();
        if ((clientFlag & clientProtocolMask) == clientProtocolMask) {
            buffer.writeShortLE(2); // server status
            buffer.writeShortLE(0); // warnings
        } else if ((clientFlag & clientTransactionsMask) == clientTransactionsMask) {
            buffer.writeShortLE(2); // server status
        }

        int clientSessionTrackMask = 1 << CapabilityFlags.CLIENT_SESSION_TRACK.ordinal();
        String humanReadableStatusInfo = "SERVER_STATUS_AUTOCOMMIT";
        if ((clientFlag & clientSessionTrackMask) == clientSessionTrackMask) {
            // TODO length encode
//            int length = humanReadableStatusInfo.getBytes(StandardCharsets.UTF_8).length;
//            buffer.writeByte(length);
//            buffer.writeCharSequence(humanReadableStatusInfo, StandardCharsets.UTF_8);
            // server state change
        } else {
            buffer.writeCharSequence(humanReadableStatusInfo, StandardCharsets.UTF_8);
        }

        int okLen = buffer.readableBytes();
        byte seqId = 0;
        ByteBuf okPacket = Unpooled.buffer(MysqlProtocolConstants.PROTOCOL_LENGTH_LENGTH + MysqlProtocolConstants.SEQ_ID_LENGTH + okLen);
        okPacket.writeMediumLE(okLen); // payload length
        okPacket.writeByte(seqId); // sequence id
        okPacket.writeBytes(buffer, 0, buffer.readableBytes()); // payload
        log.info("send ok packet:{}", Arrays.toString(okPacket.array()));
        channel.getClientChannel().writeAndFlush(okPacket);
    }

    private boolean verifyPassword(MysqlChannelContext channel, HandshakeResponsePacket packet) {
        // 校验密码：authResponse = SHA1( password ) XOR SHA1( "20-bytes random data from server" <concat> SHA1( SHA1( password ) ) )
        // 数据库存储： SHA1( SHA1( password ) ) )
        // xor逆运算： if c=a^b, then a=c^b, b=c^a
        // ==> authResponse ^ SHA1( "20-bytes random data from server" <concat> SHA1( SHA1( password ) ) ) = SHA1( password )'
        // compare SHA1(SHA1( password )') == 数据库存储?
        AuthUtils.AuthVerifyHolder authVerifyHolder = new AuthUtils.AuthVerifyHolder().setPassword(channel.getStoredPassword()).setScrambledPassword(packet.getAuthResponse()).setNonce(channel.getAuthPluginData());
        return AuthUtils.verifyPassword(MysqlPasswordAlgorithmEnums.valueOf(packet.getClientPluginName().toString()), authVerifyHolder);
    }


    public static void main(String[] args) {

        byte[] authResponse = {51, 96, -34, -59, -106, 63, 6, 56, 117, -72, 89, 10, 106, 106, -9, -84, -14, -48, 17, 49};
        byte[] random = {-7, -50, 115, -50, -123, 0, -19, 114, 66, 58, 37, 78, 84, -112, 18, 39, -21, 42, -33, -66};
        byte[] passwordSha1 = DigestUtils.sha1("123321");

//        byte[] randomSha1 = DigestUtils.sha1(random);
        byte[] doubleSha1Password = DigestUtils.sha1(passwordSha1);
        byte[] concat = new byte[random.length + doubleSha1Password.length];
        System.arraycopy(random, 0, concat, 0, random.length);
        System.arraycopy(doubleSha1Password, 0, concat, random.length, doubleSha1Password.length);
        byte[] bytes = DigestUtils.sha1(concat);

        byte[] mock = new byte[passwordSha1.length];
        for (int i = 0; i < mock.length; i++) {
            mock[i] = (byte) (passwordSha1[i] ^ bytes[i]);
        }
        log.info("");

    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]channelReadComplete");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("[Server]userEventTriggered");
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]channelWritabilityChanged");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]handlerAdded");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("[Server]handlerRemoved");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("[Server]exceptionCaught:{}", cause);
    }

}

