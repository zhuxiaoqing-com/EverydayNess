package org.evd.game.ConnService;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.evd.game.runtime.netty.AbsChannelInitializer;

final class ConnServiceClientChannelInitializer extends AbsChannelInitializer {
    private final ConnService owner;
    private final int maxFrameLength;

    ConnServiceClientChannelInitializer(ConnService owner, int maxFrameLength) {
        this.owner = owner;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        pipeline.addLast("packetHandler", new ConnServiceClientChannelHandler(owner));
    }
}
