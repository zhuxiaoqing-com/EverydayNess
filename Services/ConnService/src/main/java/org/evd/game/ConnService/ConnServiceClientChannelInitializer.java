package org.evd.game.ConnService;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.evd.game.runtime.netty.AbsChannelInitializer;
import org.evd.game.runtime.netty.ChannelManager;

final class ConnServiceClientChannelInitializer extends AbsChannelInitializer {
    private final ConnServiceClientTransport transport;
    private final ChannelManager channelManager;
    private final int maxFrameLength;

    ConnServiceClientChannelInitializer(ConnServiceClientTransport transport, ChannelManager channelManager, int maxFrameLength) {
        this.transport = transport;
        this.channelManager = channelManager;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        ConnServiceClientChannelHandler handler = new ConnServiceClientChannelHandler(transport);
        handler.setChannelManager(channelManager);
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        pipeline.addLast("packetHandler", handler);
    }
}
