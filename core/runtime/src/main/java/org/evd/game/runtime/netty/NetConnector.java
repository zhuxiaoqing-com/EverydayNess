package org.evd.game.runtime.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.WriteBufferWaterMark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetConnector {

    private static final Logger logger = LoggerFactory.getLogger(NetConnector.class);
    private static final boolean EPOLL_AVAILABLE = Epoll.isAvailable();
    private static final EventLoopGroup SHARED_GROUP =
            EPOLL_AVAILABLE ? new EpollEventLoopGroup() : new NioEventLoopGroup();

    private final Bootstrap bootstrap;
    private final String name;

    private volatile Channel channel;
    private volatile boolean shutdown;
    // 连接是否正在进行中，异步情况下有用
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private volatile ChannelFuture connectingFuture;

    public NetConnector(String name, BaseChannelInitializer handler) {
        this.name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");

        this.bootstrap = new Bootstrap();
        this.bootstrap.group(SHARED_GROUP)
                .channel(EPOLL_AVAILABLE ? EpollSocketChannel.class : NioSocketChannel.class)
                .handler(handler);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_SNDBUF, NetConstants.SERVICE_SO_SEND_BUFFER_SIZE);
        bootstrap.option(ChannelOption.SO_RCVBUF, NetConstants.SERVICE_SO_RECEIVE_BUFFER_SIZE);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, NetConstants.CONNECT_TIMEOUT_MILLIS);
        bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                NetConstants.SERVICE_WRITE_LOW_WATER_MARK,
                NetConstants.SERVICE_WRITE_HIGH_WATER_MARK));
    }

    public ChannelFuture connect(boolean isSync, InetSocketAddress address) throws InterruptedException {
        if (shutdown) {
            throw new IllegalStateException("NetConnector already shutdown: " + name);
        }
        if (address == null) {
            throw new IllegalArgumentException("connect address is empty: " + name);
        }

        Channel activeChannel = this.channel;
        if (activeChannel != null && activeChannel.isActive()) {
            logger.info("[ name: {} ] reuse active channel: remote={}", name, activeChannel.remoteAddress());
            // 返回的是一个已经完成、结果为成功的 ChannelFuture
            return activeChannel.newSucceededFuture();
        }
        ChannelFuture pendingFuture = connectingFuture;
        if (connecting.get() && pendingFuture != null && !pendingFuture.isDone()) {
            logger.info("[ name: {} ] connect already in progress: remote={}", name, address);
            return pendingFuture;
        }
        if (!connecting.compareAndSet(false, true)) {
            pendingFuture = connectingFuture;
            if (pendingFuture != null) {
                logger.info("[ name: {} ] connect skipped because another connect is in progress: remote={}",
                        name, address);
                return pendingFuture;
            }
            throw new IllegalStateException("connect state inconsistent: " + name);
        }

        ChannelFuture future = null;
        try {
            future = bootstrap.connect(address);
            connectingFuture = future;
            future.addListener((ChannelFutureListener) connectFuture -> {
                if (!connectFuture.isSuccess()) {
                    logger.error("[ name: {} ] connect to address:{} fail", name, address);
                    closeQuietly(connectFuture.channel());
                    finishConnectAttempt();
                    return;
                }
                bindChannel(connectFuture.channel());
                logger.info("[ name: {} ] connect to address:{} success", name, address);
                finishConnectAttempt();
            });

            if (isSync) {
                future.sync();
                if (!future.isSuccess()) {
                    return future;
                }
            }
            return future;
        } catch (InterruptedException e) {
            resetConnectState();
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            resetConnectState();
            logger.error("[ name: {} ] connect to address:{} fail", name, address, e);
            closeQuietly(future == null ? null : future.channel());
            return future;
        }
    }

    public ChannelFuture tryConnect(boolean isSync, InetSocketAddress address) throws InterruptedException {
        return connect(isSync, address);
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public boolean isConnecting() {
        ChannelFuture future = connectingFuture;
        return connecting.get() && future != null && !future.isDone();
    }

    public synchronized void closeChannel() {
        Channel activeChannel = this.channel;
        this.channel = null;
        closeQuietly(activeChannel);
    }

    /**
     * 关闭 remoteNode 用，只关闭当前 connector 持有的连接。
     */
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        resetConnectState();
        closeChannel();
    }

    /**
     * 进程退出时统一关闭共享的 Netty IO 线程池。
     */
    public static void shutdownSharedGroup() {
        SHARED_GROUP.shutdownGracefully();
    }

    private synchronized void bindChannel(Channel newChannel) {
        if (shutdown) {
            closeQuietly(newChannel);
            return;
        }
        this.channel = newChannel;
        newChannel.closeFuture().addListener((ChannelFutureListener) future -> clearChannel(newChannel));
    }

    private synchronized void clearChannel(Channel closedChannel) {
        if (this.channel == closedChannel) {
            this.channel = null;
        }
    }

    private void finishConnectAttempt() {
        resetConnectState();
    }

    private void resetConnectState() {
        connectingFuture = null;
        connecting.set(false);
    }

    private static void closeQuietly(Channel currentChannel) {
        if (currentChannel != null) {
            currentChannel.close();
        }
    }
}
