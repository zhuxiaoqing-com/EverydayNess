package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.RemoteNode;

@Slf4j
public final class RemoteNodeChannelHandler extends  BaseChannelHandler<ByteBuf>  {
   private final Node node;
   private final RemoteNode remoteNode;

    public RemoteNodeChannelHandler(ChannelManager channelManager, RemoteNode remoteNode) {
        super(channelManager);
        this.remoteNode = remoteNode;
        this.node = remoteNode.getLocalNode();
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = getNetChannel(ctx);
        log.info("Netty onChannelActive: node={}, remoteNode={} sessionId={}", node.getId(), remoteNode.getRemoteId(), netChannel.getChannelId());
        node.onOutboundChannelActive(remoteNode, netChannel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        node.remoteCallHandle_nt(msg, getNetChannel(ctx));
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).getAndSet(null);
        if (session != null) {
            if (session.getBrokenType() == BrokenType.NONE) {
                session.setBrokenType(BrokenType.CLIENT_CLOSE);
            }
            node.onChannelInactive_nt(session);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        String remoteNodeId = ctx.channel().attr(ServerAttributeKey.remoteNodeId).get();
        log.info("Netty onChannelInactive: node={}, remoteNode={} sessionId={}",
                node.getId(), remoteNodeId, sessionId);

    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        log.error("Netty 异常: node={}, remoteNode={} sessionId={}", node.getId(), remoteNode.getRemoteId(), sessionId, cause);
    }

}
