package org.evd.game.runtime.debug;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.netty.NetChannel;

/**
 * 统一收口 runtime 层的调试打印。
 */
@Slf4j
public final class DebugPrint {
    private DebugPrint() {
    }

    private static boolean isDebugEnabled() {
        return GlobalYml.requireNodeConfig().isDebug();
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

    /**
     * 打印收到的远程 Node 数据帧信息。
     *
     * @param nodeId         当前 Node 标识
     * @param remoteNodeId   发送方 Node 标识
     * @param sourceChannel  接收数据帧的网络连接
     * @param payloadLength  数据帧负载长度
     */
    public static void printReceiveNodeFrame(int nodeId, Integer remoteNodeId,
                                              NetChannel sourceChannel, int payloadLength) {
        if (!isDebugEnabled()) {
            return;
        }
        log.debug("NodeFrame IN node={}, remoteNode={}, channelId={}, payloadLength={}",
                nodeId,
                remoteNodeId,
                sourceChannel == null ? -1L : sourceChannel.getChannelId(),
                payloadLength);
    }

    /**
     * 打印发送的远程 Node 数据帧信息。
     *
     * @param stage         数据帧发送阶段
     * @param localNodeId   当前 Node 标识
     * @param remoteNodeId  接收方 Node 标识
     * @param netChannel    发送数据帧的网络连接
     * @param byteBuf       待发送的数据帧
     * @param call          数据帧对应的调用对象，普通数据包可以为空
     */
    public static void printSendNodeFrame(String stage, int localNodeId, int remoteNodeId,
                                          NetChannel netChannel, ByteBuf byteBuf, CallBase call) {
        if (!isDebugEnabled()) {
            return;
        }
        int frameLength = byteBuf.readableBytes();
        int payloadLength = frameLength >= Integer.BYTES
                ? byteBuf.getInt(byteBuf.readerIndex())
                : -1;
        log.debug("NodeFrame OUT stage={}, localNode={}, remoteNode={}, channelId={}, callType={}, frameLength={}, payloadLength={}",
                stage,
                localNodeId,
                remoteNodeId,
                netChannel == null ? -1L : netChannel.getChannelId(),
                call == null ? "<packet>" : call.getClass().getSimpleName(),
                frameLength,
                payloadLength);
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
