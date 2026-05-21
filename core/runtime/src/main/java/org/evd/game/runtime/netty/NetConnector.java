package org.evd.game.runtime.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public final class NetConnector implements AutoCloseable {
    private final NetConnectorConfig config;
    private final NioEventLoopGroup workerGroup;
    private final Bootstrap bootstrap;
    private volatile Channel channel;

    public NetConnector(NetConnectorConfig config, AbsChannelInitializer initializer) {
        this.config = config;
        this.workerGroup = new NioEventLoopGroup(Math.max(1, config.getWorkerThreads()));
        this.bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .handler(initializer);
    }

    public Channel connect() {
        Channel current = channel;
        if (current != null && current.isActive()) {
            return current;
        }
        synchronized (this) {
            Channel recheck = channel;
            if (recheck != null && recheck.isActive()) {
                return recheck;
            }
            ChannelFuture connectFuture = bootstrap.connect(config.getHost(), config.getPort()).awaitUninterruptibly();
            if (!connectFuture.isSuccess()) {
                throw new IllegalStateException("Netty 连接失败: " + config.getHost() + ":" + config.getPort(), connectFuture.cause());
            }
            channel = connectFuture.channel();
            return channel;
        }
    }

    @Override
    public void close() {
        Channel current = channel;
        channel = null;
        if (current != null) {
            current.close().awaitUninterruptibly();
        }
        workerGroup.shutdownGracefully().awaitUninterruptibly();
    }
}
