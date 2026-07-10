package org.evd.game.AdminService.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminService 自己持有的 HTTP 服务。
 */
public final class AdminHttpServer {
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final List<Channel> channels = new ArrayList<>();

    public AdminHttpServer(int port) {
        boolean epollAvailable = Epoll.isAvailable();
        this.bossGroup = epollAvailable ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
        this.workerGroup = epollAvailable ? new EpollEventLoopGroup() : new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        AdminHttpRouteRegistry routeRegistry = AdminHttpRouteRegistry.load();
        bootstrap.group(bossGroup, workerGroup)
                .channel(epollAvailable ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .childHandler(new AdminHttpServerInitializer(routeRegistry));
        try {
            ChannelFuture bind = bootstrap.bind(port).sync();
            channels.add(bind.channel());
        } catch (Exception e) {
            shutdown();
            throw new IllegalStateException("AdminService HTTP 启动失败: port=" + port, e);
        }
    }

    public void shutdown() {
        for (Channel channel : channels) {
            channel.close();
        }
        channels.clear();
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }
}
