package org.evd.game.runtime.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Objects;
import java.util.function.Supplier;

public class BaseChannelInitializer extends ChannelInitializer<SocketChannel> {
    protected final Supplier<? extends BaseChannelHandler<?>> baseChannelHandlerSupplier;
    protected final boolean client;// 是否是和客户端通讯

    public BaseChannelInitializer(Supplier<? extends BaseChannelHandler<?>> baseChannelHandlerSupplier, boolean client) {
        this.baseChannelHandlerSupplier = Objects.requireNonNull(baseChannelHandlerSupplier, "baseChannelHandlerSupplier");
        this.client = client;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        int maxFrameLength = isClient() ? NetConstants.MAX_FRAME_LENGTH : NetConstants.SERVICE_MAX_FRAME_LENGTH;
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
        pipeline.addLast("outboundExceptionBridge", new OutboundExceptionBridgeHandler());
        pipeline.addLast("packetHandler", baseChannelHandlerSupplier.get());
    }


    public boolean isClient() {
        return client;
    }
}
