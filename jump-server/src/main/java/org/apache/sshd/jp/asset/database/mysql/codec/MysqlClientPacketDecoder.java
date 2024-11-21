package org.apache.sshd.jp.asset.database.mysql.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.jp.asset.database.mysql.codec.packet.*;
import org.apache.sshd.jp.asset.database.mysql.common.CapabilityFlags;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlPasswordAlgorithmEnums;
import org.apache.sshd.jp.asset.database.mysql.handler.MysqlServerHandler;
import org.apache.sshd.jp.asset.database.mysql.model.MysqlChannelContext;
import org.apache.sshd.jp.asset.database.mysql.utils.PacketUtils;
import org.apache.sshd.jp.utils.ApplicationContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.sshd.jp.asset.database.mysql.codec.PacketTypes.FORWARD;

@Slf4j
public class MysqlClientPacketDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
//        log.info("channelRead:{}", in);
        byte[] dest = new byte[in.readableBytes()];
        in.getBytes(in.readerIndex(), dest);
        log.info("[Client-Decoder]channel '{}' read:{}", ctx.channel().remoteAddress(), Arrays.toString(dest));

        short head = in.getUnsignedByte(in.readerIndex() + 4);
        MysqlPacket packet = null;
        switch (head) {
            case PacketTypes.HANDSHAKE_V9, PacketTypes.HANDSHAKE_V10 -> {
                packet = parseHandshakePacket(in, ctx);
            }
            case PacketTypes.OK -> {
                packet = parseOkPacket(in, ctx);
            }
            case PacketTypes.AUTH_MORE_DATA -> {
                packet = parseAuthMoreDataPacket(in, ctx);
            }
            /*case PacketTypes.COM_QUERY -> {
                packet = parseQueryCommandPacket(in, ctx);
            }*/
            /*case PacketTypes.EOF -> {
                packet = parseEofPacket(in, ctx);
            }*/
            case PacketTypes.ERR -> {
                packet = parseErrorPacket(in, ctx);
            }
            default -> {
                packet = parseRawPacket(in, ctx);
            }
        }
        log.info("[Client-Decoder]channel '{}' parse packet:{}", ctx.channel().remoteAddress(), packet);
        out.add(packet);
    }

    private MysqlPacket parseRawPacket(ByteBuf in, ChannelHandlerContext ctx) {

        ByteBuf raw = in.copy();
        in.readBytes(in.readableBytes());
        return new MysqlPacket()
                .setHead(FORWARD)
                .setRaw(raw);
    }

    private MysqlPacket parseAuthMoreDataPacket(ByteBuf in, ChannelHandlerContext ctx) {

        ByteBuf raw = in.copy();

        int length = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        short head = in.readUnsignedByte();
        CharSequence authenticationMethodData = in.readCharSequence(in.readableBytes(), StandardCharsets.UTF_8);

        return new AuthMoreDataPacket()
                .setAuthenticationMethodData(authenticationMethodData)
                .setLength(length)
                .setSeqId(seq)
                .setHead(head)
                .setRaw(raw);
    }

    private MysqlPacket parseOkPacket(ByteBuf in, ChannelHandlerContext ctx) {

        ByteBuf raw = in.copy();

        int length = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        short head = in.readUnsignedByte();
        long affectedRows = PacketUtils.readEncodedInt(in);
        long lastInsertId = PacketUtils.readEncodedInt(in);

        MysqlServerHandler handler = ApplicationContextHolder.getBean(MysqlServerHandler.class);
        MysqlChannelContext channelContext = handler.channelContextMap.get(ctx.channel().id().asLongText());
        long capabilityFlag = channelContext.getCapabilityFlag();

        int statusFlags = 0, warnings = 0;
        CharSequence info = null;
        if (in.readableBytes() > 0) {
            if (CapabilityFlags.hasCapability(capabilityFlag, CapabilityFlags.CLIENT_PROTOCOL_41)) {
                statusFlags = in.readUnsignedShortLE();
                warnings = in.readUnsignedShortLE();
            } else if (CapabilityFlags.hasCapability(capabilityFlag, CapabilityFlags.CLIENT_TRANSACTIONS)) {
                statusFlags = in.readUnsignedShortLE();
            }
        }

        if (in.readableBytes() > 0) {
            if (CapabilityFlags.hasCapability(capabilityFlag, CapabilityFlags.CLIENT_SESSION_TRACK) && in.readableBytes() > 0) {
                long infoLength = PacketUtils.readEncodedInt(in);
                info = in.readCharSequence((int) infoLength, StandardCharsets.UTF_8);
            } else {
                info = in.readCharSequence(in.readableBytes(), StandardCharsets.UTF_8);
            }
        }

        return new OkPacket()
                .setAffectRows(affectedRows)
                .setLastInsertId(lastInsertId)
                .setStatusFlags(statusFlags)
                .setWarnings(warnings)
                .setInfo(info)
                .setLength(length)
                .setSeqId(seq)
                .setHead(head)
                .setRaw(raw);
    }

    private MysqlPacket parseErrorPacket(ByteBuf in, ChannelHandlerContext ctx) {

        int length = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        short head = in.readUnsignedByte();
        int errorCode = in.readUnsignedShortLE();

        MysqlServerHandler handler = ApplicationContextHolder.getBean(MysqlServerHandler.class);
        MysqlChannelContext channel = handler.channelContextMap.get(ctx.channel().id().asLongText());
        if (CapabilityFlags.hasCapability(channel.getCapabilityFlag(), CapabilityFlags.CLIENT_PROTOCOL_41)) {
            CharSequence sqlStateMarker = in.readCharSequence(1, StandardCharsets.UTF_8);
            CharSequence sqlState = in.readCharSequence(5, StandardCharsets.UTF_8);
        }
        CharSequence errorMsg = in.readCharSequence(in.readableBytes(), StandardCharsets.UTF_8);

        return new ErrorPacket()
                .setErrorCode(errorCode)
                .setErrorMessage(errorMsg)
                .setLength(length)
                .setSeqId(seq)
                .setHead(head);
    }

    private MysqlPacket parseHandshakePacket(ByteBuf in, ChannelHandlerContext ctx) {

        int length = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        short protocolVersion = in.readUnsignedByte();
        CharSequence serverVersion = PacketUtils.readStringTilNull(in);
        long threadId = in.readUnsignedIntLE();
        ByteBuf authPluginData1 = in.readBytes(8);
        short filter = in.readUnsignedByte();
        int capabilityLowFlag = in.readUnsignedShortLE();
        short characterSet = in.readUnsignedByte();
        int statusFlag = in.readUnsignedShortLE();
        int capabilityHighFlag = in.readUnsignedShortLE();
        long serverCapabilities = capabilityHighFlag << 16 | capabilityLowFlag;
        short authPluginDataLength = in.readUnsignedByte();
        CharSequence reserved = in.readCharSequence(10, StandardCharsets.UTF_8);
        ByteBuf authPluginData2 = in.readBytes(Math.max(13, authPluginDataLength - 8));
        CharSequence clientPluginAuthName = null;
        if (CapabilityFlags.hasCapability(serverCapabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            clientPluginAuthName = PacketUtils.readStringTilNull(in);
        }

        byte[] pluginAuthData = new byte[authPluginData1.readableBytes() + authPluginData2.readableBytes()];
        int index = authPluginData1.readableBytes();
        authPluginData1.readBytes(pluginAuthData, 0, index);
        authPluginData2.readBytes(pluginAuthData, index, authPluginData2.readableBytes());

        return new HandshakePacket()
                .setProtocolVersion(protocolVersion)
                .setServerVersion(serverVersion)
                .setThreadId(threadId)
                .setAuthPluginData(pluginAuthData)
                .setFilter(filter)
                .setCapabilityFlags(serverCapabilities)
                .setStatusFlag(statusFlag)
                .setCharacterSet(characterSet)
                .setAuthPluginDataLength(authPluginDataLength)
                .setReserved(reserved)
                .setAuthPluginName(clientPluginAuthName)
                .setLength(length)
                .setSeqId(seq)
                .setHead(protocolVersion);
    }

    public static void main(String[] args) {

        byte[] resp = {-53, 0, 0, 0, -123, -90, -1, -128, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 114, 111, 111, 116, 0, 20, 17, 10, -77, -112, 111, -31, 21, -64, -6, -33, 93, 82, 8, -11, -47, -9, -109, 41, -6, -100, 99, 97, 99, 104, 105, 110, 103, 95, 115, 104, 97, 50, 95, 112, 97, 115, 115, 119, 111, 114, 100, 0, 122, 4, 95, 112, 105, 100, 5, 49, 54, 55, 50, 52, 9, 95, 112, 108, 97, 116, 102, 111, 114, 109, 5, 65, 77, 68, 54, 52, 3, 95, 111, 115, 7, 87, 105, 110, 100, 111, 119, 115, 15, 95, 99, 108, 105, 101, 110, 116, 95, 118, 101, 114, 115, 105, 111, 110, 5, 51, 46, 50, 46, 51, 7, 95, 116, 104, 114, 101, 97, 100, 5, 49, 57, 57, 52, 52, 12, 95, 115, 101, 114, 118, 101, 114, 95, 104, 111, 115, 116, 9, 108, 111, 99, 97, 108, 104, 111, 115, 116, 12, 95, 99, 108, 105, 101, 110, 116, 95, 110, 97, 109, 101, 10, 108, 105, 98, 109, 97, 114, 105, 97, 100, 98};
        MysqlPacket mysqlPacket = parseHandshakeResponsePacket(Unpooled.wrappedBuffer(resp), null);
        System.out.println();

        System.out.println(new String(new byte[]{-124, 4, 35, 48, 56, 83, 48, 49, 71, 111, 116, 32, 112, 97, 99, 107, 101, 116, 115, 32, 111, 117, 116, 32, 111, 102, 32, 111, 114, 100, 101, 114}));

    }

    private static MysqlPacket parseHandshakeResponsePacket(ByteBuf in, ChannelHandlerContext ctx) {

        // check  Protocol::HandshakeResponse320 or Protocol::HandshakeResponse41
        int len = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        int oldVersionClientFlag = in.getUnsignedShortLE(0);
        int protocolMask = 1 << CapabilityFlags.CLIENT_PROTOCOL_41.ordinal();
        if ((oldVersionClientFlag & protocolMask) == protocolMask) {

        } else {
            long clientFlag = in.readUnsignedIntLE();
            long maxPacketLength = in.readUnsignedIntLE();
            short characterSet = in.readUnsignedByte();
            CharSequence filter = in.readCharSequence(23, StandardCharsets.UTF_8);
            CharSequence username = PacketUtils.readStringTilNull(in);
            long authResponseLength = 0;
            byte[] authResponse = null;
            if (CapabilityFlags.hasCapability(clientFlag, CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
                // 根据认证方式决定加密密码长度: mysql_native_password:20, caching_sha2_password:32
                MysqlServerHandler handler = ApplicationContextHolder.getBean(MysqlServerHandler.class);
                MysqlChannelContext channelContext = handler.channelContextMap.get(ctx.channel().id().asLongText());
                String authPluginName = channelContext.getAuthPluginName();
                int length = in.getUnsignedByte(in.readerIndex());
                if (length == 20 || length == 32) {
                    authResponseLength = PacketUtils.readEncodedInt(in);
                    authResponse = new byte[(int) authResponseLength];
                    in.readBytes(authResponse);
                } else {
                    length = MysqlPasswordAlgorithmEnums.caching_sha2_password.name().equals(authPluginName) ? 32 : 20;
                    authResponse = new byte[length];
                    in.readBytes(authResponse);
                }
            } else {
                authResponseLength = in.readUnsignedByte();
                authResponse = new byte[(int) authResponseLength];
                in.readBytes(authResponse);
            }

            CharSequence database = null;
            if (CapabilityFlags.hasCapability(clientFlag, CapabilityFlags.CLIENT_CONNECT_WITH_DB)) {
                database = PacketUtils.readStringTilNull(in);
            }

            CharSequence clientPluginName = null;
            if (CapabilityFlags.hasCapability(clientFlag, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                clientPluginName = PacketUtils.readStringTilNull(in);
            }

            Map<String, String> attr = new HashMap<>();
            if (CapabilityFlags.hasCapability(clientFlag, CapabilityFlags.CLIENT_CONNECT_ATTRS) && in.readableBytes() > 0) {
                long keyValueNum = PacketUtils.readEncodedInt(in);
                for (int i = (int) keyValueNum; i > 0; ) {
                    int start = in.readerIndex();
                    long keyNameLength = PacketUtils.readEncodedInt(in);
                    CharSequence keyName = in.readCharSequence((int) keyNameLength, StandardCharsets.UTF_8);
                    long valueLength = PacketUtils.readEncodedInt(in);
                    CharSequence value = in.readCharSequence((int) valueLength, StandardCharsets.UTF_8);
                    attr.put(keyName.toString(), value.toString());
                    i -= (in.readerIndex() - start);
                }
            }

            return new HandshakeResponsePacket()
                    .setClientFlag(clientFlag)
                    .setMaxPacketLength(maxPacketLength)
                    .setCharacterSet(characterSet)
                    .setFilter(filter)
                    .setUsername(username)
                    .setAuthResponseLength(authResponseLength)
                    .setAuthResponse(authResponse)
                    .setDatabase(database)
                    .setClientPluginName(clientPluginName)
                    .setAttr(attr)
                    .setLength(len)
                    .setSeqId(seq)
                    .setHead((short) 0x0fff);
        }

        return null;
    }

    private MysqlPacket parseQueryCommandPacket(ByteBuf in, ChannelHandlerContext ctx) {

        ByteBuf raw = in.copy();

        int length = in.readUnsignedMediumLE();
        short seq = in.readUnsignedByte();
        short head = in.readUnsignedByte();
        /*MysqlServerHandler handler = ApplicationContextHolder.getBean(MysqlServerHandler.class);
        MysqlChannelContext channel = handler.channelContextMap.get(ctx.channel().id().asLongText());
        if (CapabilityFlags.hasCapability(channel.getCapabilityFlag(), CapabilityFlags.CLIENT_QUERY_ATTRIBUTES)) {
            long parameterCount = PacketUtils.readEncodedInt(in);
            long parameterSetCount = PacketUtils.readEncodedInt(in);
            for (int i = 0; i < parameterCount; i++) {
                int nullBitmapLength = (int) ((parameterCount + 7) / 8);
                byte[] nullBitmap = new byte[nullBitmapLength];
                in.readBytes(nullBitmap);
                short newParamsBindFlag = in.readUnsignedByte();
                if (newParamsBindFlag != 1) {
                    throw new IllegalArgumentException("Malformed packet error");
                }
                int paramTypeAndFlag = in.readUnsignedShortLE();
                long parameterNameLength = PacketUtils.readEncodedInt(in);
                CharSequence parameterName = in.readCharSequence(paramTypeAndFlag, StandardCharsets.UTF_8);
                Object value = extractValueByType(in, paramTypeAndFlag);
            }
        }*/

        CharSequence sql = null;
        if (in.readableBytes() > 0) {
            sql = in.readCharSequence(in.readableBytes(), StandardCharsets.UTF_8);
        }

        return new QueryPacket()
                .setSql(sql)
                .setLength(length)
                .setSeqId(seq)
                .setHead(head)
                .setRaw(raw);
    }

    private Object extractValueByType(ByteBuf in, int paramTypeAndFlag) {
        return null;
    }
}
