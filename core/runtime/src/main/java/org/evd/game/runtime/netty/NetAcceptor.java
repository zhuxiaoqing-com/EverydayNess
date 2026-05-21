package org.evd.game.runtime.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class NetAcceptor implements AutoCloseable {
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private final Channel channel;

    public NetAcceptor(NetAcceptorConfig config, AbsChannelInitializer initializer) {
        this.bossGroup = new NioEventLoopGroup(Math.max(1, config.getBossThreads()));
        int workerThreads = config.getWorkerThreads() > 0
                ? config.getWorkerThreads()
                : Runtime.getRuntime().availableProcessors();
        this.workerGroup = new NioEventLoopGroup(workerThreads);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(initializer);
            this.channel = bootstrap.bind(config.getHost(), config.getPort()).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
            bossGroup.shutdownGracefully().awaitUninterruptibly();
            throw e;
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        channel.close().awaitUninterruptibly();
        workerGroup.shutdownGracefully().awaitUninterruptibly();
        bossGroup.shutdownGracefully().awaitUninterruptibly();
    }
}
