package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.support.LogCore;

public final class NodeChannelHandler extends  BaseChannelHandler<ByteBuf>  {
   private Node node;

    public NodeChannelHandler(ChannelManager channelManager, Node node) {
        super(channelManager);
        this.node = node;
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        LogCore.core.error("Netty onChannelActive: node={}, sessionId={}", node.getId(), netChannel.getChannelId());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (msg.readableBytes() < Integer.BYTES) {
            throw new IllegalStateException("ConnService 收到非法客户端包，长度不足 4 字节");
        }

        node.remoteCallHandle_nt(msg, ctx.channel());
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            if (session.getBrokenType() == BrokenType.NONE) {
                session.setBrokenType(BrokenType.CLIENT_CLOSE);
            }
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        String remoteNodeId = ctx.channel().attr(ServerAttributeKey.remoteNodeId).get();
        LogCore.core.error("Netty onChannelInactive: node={}, remoteNode={}, sessionId={}",
                node.getId(), remoteNodeId, sessionId);

        node.onInboundChannelInactive_nt(ctx.channel());
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        LogCore.core.error("Netty 异常: node={}, sessionId={}", node.getId(), sessionId, cause);
    }

}
