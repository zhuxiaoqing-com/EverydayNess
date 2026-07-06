package org.evd.game.runtime.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public final class NetConnector {

    private static final Logger logger = LoggerFactory.getLogger(NetConnector.class);

    private EventLoopGroup group;
    private Bootstrap b;

    private String name;


    public NetConnector(String name, BaseChannelInitializer handler) {
        this.group = isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        this.b = new Bootstrap();
        this.b.group(group).channel(isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class).handler(handler);
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.SO_SNDBUF, NetConstants.SERVICE_SO_SEND_BUFFER_SIZE);
        b.option(ChannelOption.SO_RCVBUF, NetConstants.SERVICE_SO_RECEIVE_BUFFER_SIZE);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, NetConstants.CONNECT_TIMEOUT_MILLIS);
        this.name = name;
    }

    private boolean isAvailable() {
        return Epoll.isAvailable();
    }

    public ChannelFuture connect(boolean isSync, InetSocketAddress... address) throws InterruptedException {
        for (InetSocketAddress addr : address) {
            ChannelFuture future = null;
            try {
                if (isSync) {
                    future = b.connect(addr).sync();
                } else {
                    future = b.connect(addr);
                }
                logger.info("$$$$$$$$$$$$ [ name: {}   ] connect to address:{} success $$$$$$$$$$$$$$$$", name, addr);
                return future;
            } catch (Exception e) {
                logger.error("!!!!!!!!!!!! [ name: {}   ] connect to address:{} fail !!!!!!!!", name, address);
                if (future != null) {
                    future.channel().close();
                }
                shutdown();
            }
            return null;
        }
        return null;
    }

    /**
     * 关闭
     */
    public void shutdown() {
        if (this.group != null)
            this.group.shutdownGracefully();
    }
}
