package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public abstract class ByteArrayChannelHandler extends BaseChannelHandler<ByteBuf> {
    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] payload = new byte[msg.readableBytes()];
        msg.readBytes(payload);
        handlePacket(ctx, payload);
    }

    protected abstract void handlePacket(ChannelHandlerContext ctx, byte[] payload) throws Exception;
}
