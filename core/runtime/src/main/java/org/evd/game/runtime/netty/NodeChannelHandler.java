package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.Node;


@Slf4j
public final class NodeChannelHandler extends  BaseChannelHandler<ByteBuf>  {
   private Node node;

    public NodeChannelHandler(ChannelManager channelManager, Node node) {
        super(channelManager);
        this.node = node;
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        log.info("Netty onChannelActive: node={}, sessionId={}", node.getId(), netChannel.getChannelId());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        node.remoteCallHandle_nt(msg, getNetChannel(ctx));
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            session.setBrokenType(BrokenType.CLIENT_CLOSE);
            node.onChannelInactive_nt(session);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        Integer remoteNodeId = ctx.channel().attr(ServerAttributeKey.remoteNodeId).get();
        log.info("Netty onChannelInactive: node={}, remoteNode={}, sessionId={}",
                node.getId(), remoteNodeId, sessionId);
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        log.error("Netty 异常: node={}, sessionId={}", node.getId(), sessionId, cause);
    }

}
