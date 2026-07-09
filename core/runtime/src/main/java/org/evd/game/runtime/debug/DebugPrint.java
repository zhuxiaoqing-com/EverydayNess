package org.evd.game.runtime.debug;

import io.netty.buffer.ByteBufUtil;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.netty.NetChannel;

/**
 * 统一收口 runtime 层的调试打印。
 */
@Slf4j
public final class DebugPrint {
    private DebugPrint() {
    }

    private static boolean isDebugEnabled() {
        return GlobalConfig.requireNodeConfig().isDebug();
    }

    public static void printReceiveRpc(CallBase call) {
        if (isDebugEnabled()) {
            log.warn("收到rpc call {}", call);
        }
    }

    public static void printSendRpc(NetChannel netChannel, CallBase call) {
        if (isDebugEnabled()) {
            long channelId = netChannel == null ? -1L : netChannel.getChannelId();
            log.warn("发送rpc channelId {} call {}", channelId, call);
        }
    }

    public static void printReceiveClientCmd(NetChannel netChannel, int msgId, byte[] payload) {
        if (!isDebugEnabled()) {
            return;
        }
        long sessionId = netChannel == null ? -1L : netChannel.getChannelId();
        String remoteAddress = netChannel == null ? "unknown" : netChannel.getRemoteAddress();
        int bodyLength = Math.max(0, payload.length - Integer.BYTES);
        String bodyHex = bodyLength == 0 ? "" : ByteBufUtil.hexDump(payload, Integer.BYTES, bodyLength);
        log.warn("收到客户端协议 sessionId {} remote {} msgId {} bodyLength {} bodyHex {}",
                sessionId, remoteAddress, msgId, bodyLength, bodyHex);
    }

    public static void printSendClientCmd(NetChannel netChannel, int msgId, byte[] bodyBytes) {
        if (!isDebugEnabled()) {
            return;
        }
        long sessionId = netChannel == null ? -1L : netChannel.getChannelId();
        String remoteAddress = netChannel == null ? "unknown" : netChannel.getRemoteAddress();
        int bodyLength = bodyBytes == null ? 0 : bodyBytes.length;
        String bodyHex = bodyLength == 0 ? "" : ByteBufUtil.hexDump(bodyBytes);
        log.warn("发送客户端协议 sessionId {} remote {} msgId {} bodyLength {} bodyHex {}",
                sessionId, remoteAddress, msgId, bodyLength, bodyHex);
    }
}
