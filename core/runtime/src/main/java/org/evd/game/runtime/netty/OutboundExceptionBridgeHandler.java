package org.evd.game.runtime.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;


public final class OutboundExceptionBridgeHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ChannelPromise bridgedPromise = promise.unvoid();
        bridgedPromise.addListener(future -> {
            if (!future.isSuccess()) {
                ctx.fireExceptionCaught(future.cause());
            }
        });
        ctx.write(msg, bridgedPromise);
    }
}

