package org.evd.game.ConnService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.support.LogCore;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;

final class ConnServiceClientChannelHandler extends ByteArrayChannelHandler {
    private final ConnServiceClientTransport transport;

    public ConnServiceClientChannelHandler(ChannelManager channelManager, ConnServiceClientTransport transport) {
        super(channelManager);
        this.transport = transport;
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        netChannel.getSessionRef().setGate(new CallPoint(transport.owner.getNode().getId(), transport.owner.getId()));
        transport.onClientChannelActive(netChannel);
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, byte[] payload) {
        NetChannel session = requireSession(ctx.channel());
        if (payload.length < Integer.BYTES) {
            throw new IllegalStateException("ConnService 收到非法客户端包，长度不足 4 字节");
        }
        int msgId = ByteBuffer.wrap(payload, 0, Integer.BYTES).getInt();
        if (!checkMsgFlowRate(ctx, session, msgId)) {
            return;
        }
        //byte[] body = Arrays.copyOfRange(payload, Integer.BYTES, payload.length);
        transport.onClientPacket(session, msgId, new Chunk(payload, Integer.BYTES, payload.length));
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            if (session.getBrokenType() == BrokenType.NONE) {
                session.setBrokenType(BrokenType.CLIENT_CLOSE);
            }
            transport.onClientChannelInactive(session);
        }
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        transport.onClientChannelException(session, cause);
    }

    private NetChannel requireSession(Channel channel) {
        NetChannel session = channel.attr(ServerAttributeKey.netChannel).get();
        if (session == null) {
            throw new IllegalStateException("ConnService channel session not initialized");
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
            session.setBrokenType(BrokenType.MSG_FLOW_LIMIT);
            String messages = session.getFrequentlyMessageList().stream()
                    .map(e -> e.getCurrTime() + "---" + e.getCmd())
                    .collect(Collectors.joining(System.lineSeparator()));
            LogCore.core.error("ConnService 主动断开连接，消息过于频繁: service={}, sessionId={}, userId={}, remote={}",
                    transport.getOwnerServiceId(), session.getChannelId(), session.getUserId(), session.getRemoteAddress());
            LogCore.core.error("ConnService 高频消息明细: service={}, sessionId={}, messages={}",
                    transport.getOwnerServiceId(), session.getChannelId(), System.lineSeparator() + messages);
            session.setFrequentlyMessageCount(0);
            session.getFrequentlyMessageList().clear();
            ctx.close();
            return false;
        }
        session.setLastMessageTime(curTime);
        return true;
    }
}
