package org.evd.game.ConnService.client;

import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.support.LogCore;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;

final class ConnServiceClientChannelHandler extends ByteArrayChannelHandler {
    private final ConnClientConnection connection;

    ConnServiceClientChannelHandler(ConnClientConnection connection) {
        super(connection.channelManager());
        this.connection = connection;
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        netChannel.setGate(connection.owner().getNode().getCallPoint(connection.owner().getId()));
        connection.postClientChannelActive(netChannel);
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, byte[] payload) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (payload.length < Integer.BYTES) {
            throw new IllegalStateException("ConnService 收到非法客户端包，长度不足 4 字节");
        }
        int msgId = ByteBuffer.wrap(payload, 0, Integer.BYTES).getInt();
        DebugPrint.printReceiveClientCmd(session, msgId, payload);
        if (!checkMsgFlowRate(ctx, session, msgId)) {
            return;
        }
        connection.postClientPacket(session, msgId, new Chunk(payload, Integer.BYTES, payload.length - Integer.BYTES));
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            connection.postClientChannelInactive(session);
        }
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        LogCore.core.error("ConnService Netty 异常: service={}, sessionId={}",
                connection.owner().getId(), sessionId, cause);
    }

    private boolean checkMsgFlowRate(ChannelHandlerContext ctx, NetChannel session, int msgId) {
        long curTime = System.currentTimeMillis();
        if ((curTime - session.getLastMessageTime()) < NetChannel.MESSAGE_GW_TIME) {
            int cnt = session.getFrequentlyMessageCount();
            session.setFrequentlyMessageCount(cnt + 1);
            session.getFrequentlyMessageList().add(new HisMessage(msgId, null, curTime));
        } else {
            session.setFrequentlyMessageCount(0);
            session.getFrequentlyMessageList().clear();
        }
        if (session.getFrequentlyMessageCount() >= NetChannel.MESSAGE_GW_COUNT) {
            session.setBrokenType(BrokenType.MSG_FLOW_LIMIT);
            String messages = session.getFrequentlyMessageList().stream()
                    .map(e -> e.getCurrTime() + "---" + e.getCmd())
                    .collect(Collectors.joining(System.lineSeparator()));
            LogCore.core.error("ConnService 主动断开连接，消息过于频繁: service={}, sessionId={}, userId={}, remote={}",
                    connection.owner().getId(), session.getChannelId(), session.getUserId(), session.getRemoteAddress());
            LogCore.core.error("ConnService 高频消息明细: service={}, sessionId={}, messages={}",
                    connection.owner().getId(), session.getChannelId(), System.lineSeparator() + messages);
            session.setFrequentlyMessageCount(0);
            session.getFrequentlyMessageList().clear();
            ctx.close();
            return false;
        }
        session.setLastMessageTime(curTime);
        return true;
    }
}
