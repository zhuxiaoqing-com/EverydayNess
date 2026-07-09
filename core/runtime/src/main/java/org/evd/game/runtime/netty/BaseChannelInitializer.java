package org.evd.game.runtime.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

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
        // 出站顺序是从 tail 往 head 走：
        // packetHandler -> byteArrayEncoder -> frameEncoder -> outboundProbe -> socket
        // 这样业务层写入的 byte[] 会先被包成 ByteBuf，再补 4 字节帧长，最后由探针看到真实出站帧。
        pipeline.addLast("outboundExceptionBridge", new OutboundExceptionBridgeHandler());
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        pipeline.addLast("byteArrayEncoder", new ByteArrayEncoder());
        pipeline.addLast("packetHandler", baseChannelHandlerSupplier.get());
    }


    public boolean isClient() {
        return client;
    }
}
