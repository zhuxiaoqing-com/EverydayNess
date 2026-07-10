package org.evd.game.AdminService.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * AdminService HTTP pipeline。
 */
public final class AdminHttpServerInitializer extends ChannelInitializer<SocketChannel> {
    private final AdminHttpRouteRegistry routeRegistry;

    public AdminHttpServerInitializer(AdminHttpRouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024));
        pipeline.addLast("httpHandler", new AdminHttpServerInboundHandler(routeRegistry));
    }
}
