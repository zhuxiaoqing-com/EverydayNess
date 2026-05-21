package org.evd.game.runtime.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


public abstract class BaseChannelHandler<T> extends SimpleChannelInboundHandler<T> {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        onChannelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        onChannelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        onChannelException(ctx, cause);
        ctx.close();
    }

    protected void onChannelActive(ChannelHandlerContext ctx) throws Exception {
    }

    protected void onChannelInactive(ChannelHandlerContext ctx) throws Exception {
    }

    protected void onChannelException(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
}
