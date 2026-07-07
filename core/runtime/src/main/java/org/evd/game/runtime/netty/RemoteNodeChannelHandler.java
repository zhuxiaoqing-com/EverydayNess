package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.RemoteNode;
import org.evd.game.runtime.support.LogCore;

public final class RemoteNodeChannelHandler extends  BaseChannelHandler<ByteBuf>  {
   private Node node;
   private RemoteNode remoteNode;

    public RemoteNodeChannelHandler(ChannelManager channelManager, RemoteNode remoteNode) {
        super(channelManager);
        this.remoteNode = remoteNode;
        this.node = remoteNode.getLocalNode();
    }


    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        NetChannel netChannel = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        LogCore.core.error("Netty onChannelActive: node={}, remoteNode={} sessionId={}", node.getId(), remoteNode.getRemoteId(), netChannel.getChannelId());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] payload = new byte[msg.readableBytes()];
        msg.readBytes(payload);
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (payload.length < Integer.BYTES) {
            throw new IllegalStateException("ConnService 收到非法客户端包，长度不足 4 字节");
        }

        node.remoteCallHandle_nt(msg);
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
        LogCore.core.error("Netty onChannelInactive: node={}, remoteNode={} sessionId={}", node.getId(), remoteNode.getRemoteId(), sessionId);

        node.onRemoteNodeDisconnected_nt(remoteNode);
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        if (session != null) {
            session.setBrokenType(BrokenType.NETTY_EXCEPTION);
        }
        long sessionId = session == null ? -1L : session.getChannelId();
        LogCore.core.error("Netty 异常: node={}, remoteNode={} sessionId={}", node.getId(), remoteNode.getRemoteId(), sessionId, cause);
    }

}
