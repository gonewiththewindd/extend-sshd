package org.apache.sshd.jp.asset.database.mysql.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.sshd.jp.asset.database.mysql.codec.PacketTypes;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.AuthMoreDataPacket;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.HandshakePacket;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.MysqlPacket;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.OkPacket;
import org.apache.sshd.jp.asset.database.mysql.common.CapabilityFlags;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlPasswordAlgorithmEnums;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlProtocolConstants;
import org.apache.sshd.jp.asset.database.mysql.model.MysqlChannelContext;
import org.apache.sshd.jp.asset.database.mysql.utils.AuthUtils;
import org.apache.sshd.jp.asset.database.mysql.utils.PacketUtils;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@ChannelHandler.Sharable
@Component
public class MysqlClientHandler extends SimpleChannelInboundHandler<MysqlPacket> {

    @Autowired
    private MysqlServerHandler mysqlServerHandler;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]channelRegistered");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]channelUnregistered");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]channelActive");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]channelInactive");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, MysqlPacket packet) throws Exception {

        short head = packet.getHead();
        switch (head) {
            case PacketTypes.HANDSHAKE_V9, PacketTypes.HANDSHAKE_V10 -> {
                processHandshakePacket((HandshakePacket) packet, ctx);
            }
            case PacketTypes.OK -> {
                processOkPacket((OkPacket) packet, ctx);
            }
/*            case PacketTypes.AUTH_MORE_DATA -> {
                // ignore
            }*/
            default -> {
                forwardToClientChannel(packet, ctx);
            }
        }
    }

    private void forwardToClientChannel(MysqlPacket packet, ChannelHandlerContext ctx) {
        // 转发结果
        MysqlChannelContext channelContext = mysqlServerHandler.channelContextMap.get(ctx.channel().id().asLongText());
        log.info("[Client]forward packet to client channel '{}'...packet:{}", channelContext.getClientChannel().remoteAddress(), packet);
        channelContext.getClientChannel().writeAndFlush(packet.getRaw());
    }

    private void processAuthMoreDataPacket(AuthMoreDataPacket packet, ChannelHandlerContext ctx) {
        // TODO 除了认证成功以外，是否还有其他操作可能应答OK包
        MysqlChannelContext channelContext = mysqlServerHandler.channelContextMap.get(ctx.channel().id().asLongText());
        log.info("[Client]forward auth more data packet to client channel...packet:{}", packet);
        channelContext.getClientChannel().writeAndFlush(packet.getRaw());
    }

    private void processOkPacket(OkPacket packet, ChannelHandlerContext ctx) {
        // TODO 除了认证成功以外，是否还有其他操作可能应答OK包
        MysqlChannelContext channelContext = mysqlServerHandler.channelContextMap.get(ctx.channel().id().asLongText());
        if (!channelContext.isAuthenticated()) {
            channelContext.setAuthenticated(true);
        }else{
        }
        log.info("[Client]forward ok packet to client channel '{}'...packet:{}", channelContext.getClientChannel().remoteAddress(), packet);
        channelContext.getClientChannel().writeAndFlush(packet.getRaw());
    }

    private void processHandshakePacket(HandshakePacket handshakePacket, ChannelHandlerContext ctx) {

        Asset asset = AssetService.lookupAsset("4");

        MysqlChannelContext channelContext = mysqlServerHandler.channelContextMap.get(ctx.channel().id().asLongText());

        long capabilityFlag = CapabilityFlags.toLong(CapabilityFlags.getImplicitCapabilities());
        long maxPacketSize = channelContext.getMaxPacketLength();
        short characterSet = channelContext.getCharacterSet();

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeIntLE((int) capabilityFlag);
        buffer.writeIntLE((int) maxPacketSize);
        buffer.writeByte(characterSet);
        buffer.writeZero(23);
        buffer.writeCharSequence(asset.getUsername(), StandardCharsets.UTF_8);
        buffer.writeZero(1);
        // 密码加密
        AuthUtils.AuthDigestHolder holder = new AuthUtils.AuthDigestHolder(asset.getPassword(), handshakePacket.getAuthPluginData());
        MysqlPasswordAlgorithmEnums algorithm = MysqlPasswordAlgorithmEnums.valueOf(handshakePacket.getAuthPluginName().toString());
        byte[] authResponse = AuthUtils.digestPassword(holder, algorithm);
        if (CapabilityFlags.hasCapability(handshakePacket.getCapabilityFlags(), CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
            byte[] length = PacketUtils.encodeIntLE(authResponse.length);
            buffer.writeBytes(length);
            buffer.writeBytes(authResponse);
        } else {
            buffer.writeByte(authResponse.length);
            buffer.writeBytes(authResponse);
        }

        if (CapabilityFlags.hasCapability(capabilityFlag, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            buffer.writeCharSequence(handshakePacket.getAuthPluginName(), StandardCharsets.UTF_8);
            buffer.writeZero(1);
        }
        // 连接参数
        if (CapabilityFlags.hasCapability(capabilityFlag, CapabilityFlags.CLIENT_CONNECT_ATTRS) && Objects.nonNull(channelContext.getAttr())) {
            ByteBuf attr = Unpooled.buffer();
            channelContext.getAttr().forEach((k, v) -> {
                byte[] keyNameLength = PacketUtils.encodeIntLE(k.getBytes(StandardCharsets.UTF_8).length);
                attr.writeBytes(keyNameLength);
                attr.writeCharSequence(k, StandardCharsets.UTF_8);
                byte[] valueLength = PacketUtils.encodeIntLE(v.getBytes(StandardCharsets.UTF_8).length);
                attr.writeBytes(valueLength);
                attr.writeCharSequence(v, StandardCharsets.UTF_8);
            });
            byte[] length = PacketUtils.encodeIntLE(attr.readableBytes());
            buffer.writeBytes(length);
            buffer.writeBytes(attr);
        }

        int length = buffer.readableBytes();
        int seq = handshakePacket.getSeqId();
        ByteBuf handshakeResponsePacket = Unpooled.buffer(MysqlProtocolConstants.PROTOCOL_LENGTH_LENGTH + MysqlProtocolConstants.SEQ_ID_LENGTH + length);
        handshakeResponsePacket.writeMediumLE(length);
        handshakeResponsePacket.writeByte(++seq);
        handshakeResponsePacket.writeBytes(buffer);
        log.info("[Client]send packet HandshakeResponse:{}", Arrays.toString(handshakeResponsePacket.array()));
        ctx.writeAndFlush(handshakeResponsePacket);
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
        log.info("[Client]channelReadComplete");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("[Client]userEventTriggered");
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]channelWritabilityChanged");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]handlerAdded");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("[Client]handlerRemoved");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("[Client]exceptionCaught:{}", cause);
    }

}

