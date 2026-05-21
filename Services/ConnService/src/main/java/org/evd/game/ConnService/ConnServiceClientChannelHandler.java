package org.evd.game.ConnService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.netty.ByteArrayChannelHandler;
import org.evd.game.runtime.netty.HisMessage;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.netty.ServerAttributeKey;
import org.evd.game.runtime.support.LogCore;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

final class ConnServiceClientChannelHandler extends ByteArrayChannelHandler {
    private final ConnService owner;

    ConnServiceClientChannelHandler(ConnService owner) {
        this.owner = owner;
    }

    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel session = owner.createClientSession(ctx.channel());
        ctx.channel().attr(ServerAttributeKey.netChannel).set(session);
        owner.onClientChannelActive(session, ctx.channel());
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, byte[] payload) {
        NetChannel session = requireSession(ctx.channel());
        if (payload.length < Integer.BYTES) {
            throw new IllegalStateException("ConnService 收到非法客户端包，长度不足 4 字节: service=" + owner.getId());
        }
        int msgId = ByteBuffer.wrap(payload, 0, Integer.BYTES).getInt();
        if (!checkMsgFlowRate(ctx, session, msgId)) {
            return;
        }
        byte[] body = Arrays.copyOfRange(payload, Integer.BYTES, payload.length);
        owner.onClientPacket(session, msgId, body);
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            owner.onClientChannelInactive(session);
        }
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        owner.onClientChannelException(session, cause);
    }

    private NetChannel requireSession(Channel channel) {
        NetChannel session = channel.attr(ServerAttributeKey.netChannel).get();
        if (session == null) {
            throw new IllegalStateException("ConnService channel session not initialized: service=" + owner.getId());
        }
        return session;
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
            String messages = session.getFrequentlyMessageList().stream()
                    .map(e -> e.getCurrTime() + "---" + e.getCmd())
                    .collect(Collectors.joining(System.lineSeparator()));
            LogCore.core.error("ConnService 主动断开连接，消息过于频繁: service={}, sessionId={}, userId={}, remote={}",
                    owner.getId(), session.getChannelId(), session.getUserId(), session.getRemoteAddress());
            LogCore.core.error("ConnService 高频消息明细: service={}, sessionId={}, messages={}",
                    owner.getId(), session.getChannelId(), System.lineSeparator() + messages);
            session.setFrequentlyMessageCount(0);
            session.getFrequentlyMessageList().clear();
            ctx.close();
            return false;
        }
        session.setLastMessageTime(curTime);
        return true;
    }
}
