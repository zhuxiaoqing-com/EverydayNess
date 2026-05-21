package org.evd.game.ConnService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.evd.game.runtime.Session;
import org.evd.game.runtime.netty.ByteArrayChannelHandler;

final class ConnServiceClientChannelHandler extends ByteArrayChannelHandler {
    private final ConnService owner;

    ConnServiceClientChannelHandler(ConnService owner) {
        this.owner = owner;
    }

    @Override
    protected void onChannelActive(ChannelHandlerContext ctx) {
        Session session = owner.createClientSession(ctx.channel());
        ctx.channel().attr(ConnService.SESSION_KEY).set(session);
        owner.onClientChannelActive(session, ctx.channel());
    }

    @Override
    protected void handlePacket(ChannelHandlerContext ctx, byte[] payload) {
        Session session = requireSession(ctx.channel());
        int msgId = owner.decodeMsgId(payload);
        byte[] body = owner.decodeBody(payload);
        owner.onClientPacket(session, msgId, body);
    }

    @Override
    protected void onChannelInactive(ChannelHandlerContext ctx) {
        Session session = ctx.channel().attr(ConnService.SESSION_KEY).getAndSet(null);
        if (session != null) {
            owner.onClientChannelInactive(session);
        }
    }

    @Override
    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) {
        Session session = ctx.channel().attr(ConnService.SESSION_KEY).get();
        owner.onClientChannelException(session, cause);
    }

    private Session requireSession(Channel channel) {
        Session session = channel.attr(ConnService.SESSION_KEY).get();
        if (session == null) {
            throw new IllegalStateException("ConnService channel session not initialized: service=" + owner.getId());
        }
        return session;
    }
}
