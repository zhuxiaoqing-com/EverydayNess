package org.evd.game.runtime.netty;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.util.Objects;

public class ByteArrayChannelInitializer extends AbsChannelInitializer {
    private final int maxFrameLength;
    private final ByteArrayChannelHandler handler;

    public ByteArrayChannelInitializer(int maxFrameLength, ByteArrayChannelHandler handler) {
        this.maxFrameLength = maxFrameLength;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4))
                .addLast("frameEncoder", new LengthFieldPrepender(4))
                .addLast("packetHandler", handler);
    }
}
