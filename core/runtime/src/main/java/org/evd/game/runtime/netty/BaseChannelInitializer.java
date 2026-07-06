package org.evd.game.runtime.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

public class BaseChannelInitializer extends ChannelInitializer<SocketChannel> {
    protected BaseChannelHandler<?> baseChannelHandler;
    protected boolean client;// 是否是和客户端通讯

    public BaseChannelInitializer(BaseChannelHandler<?> baseChannelHandler, boolean client) {
        this.baseChannelHandler = baseChannelHandler;
        this.client = client;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        int maxFrameLength = isClient() ? NetConstants.MAX_FRAME_LENGTH : NetConstants.SERVICE_MAX_FRAME_LENGTH;
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        pipeline.addLast("packetHandler", baseChannelHandler);
    }


    public boolean isClient() {
        return client;
    }
}
