package org.evd.game.runtime.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.evd.game.runtime.support.LogCore;

import java.util.Objects;

public abstract class BaseChannelHandler<T> extends SimpleChannelInboundHandler<T> {
    private ChannelManager channelManager;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NetChannel session = new NetChannel(ctx.channel());
        ctx.channel().attr(ServerAttributeKey.netChannel).set(session);
        requireChannelManager().addChannel(session);
        LogCore.core.info("BaseChannelHandler 添加连接: handler={}, sessionId={}, remote={}",
                getClass().getSimpleName(), session.getChannelId(), session.getRemoteAddress());
        onChannelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NetChannel session = ctx.channel().attr(ServerAttributeKey.netChannel).get();
        try {
            onChannelInactive(ctx);
        } finally {
            requireChannelManager().removeChannel(ctx.channel());
            if (session != null) {
                LogCore.core.info("BaseChannelHandler 移除连接: handler={}, sessionId={}, remote={}",
                        getClass().getSimpleName(), session.getChannelId(), session.getRemoteAddress());
            } else {
                LogCore.core.warn("BaseChannelHandler 移除连接时 session 缺失: handler={}, channel={}",
                        getClass().getSimpleName(), ctx.channel());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        onChannelException(ctx, cause);
        ctx.close();
    }

    public void setChannelManager(ChannelManager channelManager) {
        this.channelManager = Objects.requireNonNull(channelManager, "channelManager");
    }

    protected final ChannelManager requireChannelManager() {
        if (channelManager == null) {
            throw new IllegalStateException("BaseChannelHandler 未设置 ChannelManager");
        }
        return channelManager;
    }

    protected void onChannelActive(ChannelHandlerContext ctx) throws Exception {
    }

    protected void onChannelInactive(ChannelHandlerContext ctx) throws Exception {
    }

    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
}
