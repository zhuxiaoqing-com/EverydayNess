package org.evd.game.AdminService.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.evd.game.runtime.Service;

/**
 * AdminService HTTP pipeline。
 */
public final class AdminHttpServerInitializer extends ChannelInitializer<SocketChannel> {
    private final AdminHttpRouteRegistry routeRegistry;
    private final Service service;

    public AdminHttpServerInitializer(AdminHttpRouteRegistry routeRegistry) {
        this(routeRegistry, null);
    }

    public AdminHttpServerInitializer(AdminHttpRouteRegistry routeRegistry, Service service) {
        this.routeRegistry = routeRegistry;
        this.service = service;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024));
        pipeline.addLast("httpHandler", new AdminHttpServerInboundHandler(routeRegistry, service));
    }
}
