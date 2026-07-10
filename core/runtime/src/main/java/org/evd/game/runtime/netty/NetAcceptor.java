package org.evd.game.runtime.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.WriteBufferWaterMark;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public final class NetAcceptor  {
    private static final String NETTY_ACCEPTOR_BACKLOG="netty.acceptor.backlog";
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ServerBootstrap b;
    private List<Channel> channel = new ArrayList<>();

    public NetAcceptor(InetSocketAddress address, BaseChannelInitializer handler) {
        // handler
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        this.b = new ServerBootstrap();
        int backlog=128;
        String rbsz=System.getProperty(NETTY_ACCEPTOR_BACKLOG);
        if(rbsz!=null) {
            backlog=Integer.parseInt(rbsz);
        }
        b.option(ChannelOption.SO_BACKLOG, backlog);
        b.option(ChannelOption.SO_REUSEADDR, true);
        b.childOption(ChannelOption.TCP_NODELAY, true);
        b.childOption(ChannelOption.SO_SNDBUF, handler.isClient()
                ? NetConstants.SO_SEND_BUFFER_SIZE : NetConstants.SERVICE_SO_SEND_BUFFER_SIZE);
        b.childOption(ChannelOption.SO_RCVBUF, handler.isClient()
                ? NetConstants.SO_RECEIVE_BUFFER_SIZE : NetConstants.SERVICE_SO_RECEIVE_BUFFER_SIZE);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        configureWriteWaterMark(handler);

        // b.option(option, value)

        // cfg.setReuseAddress(true);
        // cfg.setTcpNoDelay(true);
        // cfg.setKeepAlive(true);
        // cfg.setSoLinger(0);

        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(handler);
        try {
            this.channel.add(b.bind(address).sync().channel());
        } catch (Exception e) {
            this.workerGroup.shutdownGracefully();
            this.bossGroup.shutdownGracefully();
            throw new RuntimeException("error", e);
        }
    }

    /**
     * 我看网上说的 有两种tcp线程模型，一种就是和netty一样用一个专门的线程接收，导致一次额外的进程上下文切换的开销。
     * 第二个问题是如果流量特别大的时候 dispatcher 进程很容易成为制约整个服务 qps 提升的瓶颈。
     * 所以又有了一种并发accept，但是这样的话锁冲突太频繁了，所以干脆弄了SO_REUSEPORT,内核三次握手结束后，负载均衡分配
     *https://www.zhihu.com/question/51618274
     * 所以这个 SO_REUSEPORT 在Netty这里没啥用
     *
     * EpollEventLoopGroup 和 NioEventLoopGroup 区别：
     * EpollEventLoopGroup netty使用了linux原生的Epoll，可以使用一些Linux 特有能力。
     *      比如 SO_REUSEPORT、TCP_FASTOPEN、TCP_DEFER_ACCEPT、TCP_CORK、TCP_QUICKACK、边缘触发 EPOLL_MODE
     * NioEventLoopGroup 是java抽象过的NIO，linux下也会使用Epoll;
     *
     * NioEventLoopGroup
     * -> JDK Selector
     * -> 通常是 LT 语义
     * -> 没读完，下轮仍可能继续得到 read ready
     *
     * EpollEventLoopGroup
     * -> Netty native epoll
     * -> 默认 ET
     * -> Netty 自己负责正确的 accept/read/write 循环
     *
     * bossGroup 最好默认指定1个线程，是用来处理accept的，你用默认0自动取的2*机器总cpu,会创建多个loop,虽然不会用到，但是还是手动申明一下好；
     *
     * @param port
     * @param handler
     */
    public NetAcceptor(int port, BaseChannelInitializer handler) {
        // handler
        this.bossGroup = isAvailable() ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
        this.workerGroup = isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        this.b = new ServerBootstrap();
        int backlog=1024;
        String rbsz=System.getProperty(NETTY_ACCEPTOR_BACKLOG);
        if(rbsz!=null) {
            backlog=Integer.parseInt(rbsz);
        }
        b.option(ChannelOption.SO_BACKLOG, backlog);
        b.childOption(ChannelOption.TCP_NODELAY, true);
        b.childOption(ChannelOption.SO_SNDBUF, handler.isClient()
                ? NetConstants.SO_SEND_BUFFER_SIZE : NetConstants.SERVICE_SO_SEND_BUFFER_SIZE);
        b.childOption(ChannelOption.SO_RCVBUF, handler.isClient()
                ? NetConstants.SO_RECEIVE_BUFFER_SIZE : NetConstants.SERVICE_SO_RECEIVE_BUFFER_SIZE);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        configureWriteWaterMark(handler);

       /* if (isAvailable()) {
            b.option(EpollChannelOption.SO_REUSEPORT, true);
        }*/

        // b.option(option, value)

        // cfg.setReuseAddress(true);
        // cfg.setTcpNoDelay(true);
        // cfg.setKeepAlive(true);
        // cfg.setSoLinger(0);

        b.group(bossGroup, workerGroup).channel(isAvailable() ? EpollServerSocketChannel.class: NioServerSocketChannel.class).childHandler(handler);
        try {
            /*if (isAvailable()) {
                // linux系统下使用SO_REUSEPORT特性，使得多个线程绑定同一个端口
                int cpuNum = Runtime.getRuntime().availableProcessors();
                for (int i = 0; i < cpuNum; i++) {
                    ChannelFuture future = b.bind(port).sync();
                    this.channel.add(future.channel());
                }
            } else {
                ChannelFuture bind = b.bind(port).sync();
                this.channel.add(bind.channel());
            }*/

            ChannelFuture bind = b.bind(port).sync();
            this.channel.add(bind.channel());
        } catch (Exception e) {
            this.workerGroup.shutdownGracefully();
            this.bossGroup.shutdownGracefully();
            throw new RuntimeException("error port:" + port, e);
        }
    }

    private boolean isAvailable() {
        return Epoll.isAvailable();
    }

    private void configureWriteWaterMark(BaseChannelInitializer handler) {
        int low = handler.isClient()
                ? NetConstants.CLIENT_WRITE_LOW_WATER_MARK
                : NetConstants.SERVICE_WRITE_LOW_WATER_MARK;
        int high = handler.isClient()
                ? NetConstants.CLIENT_WRITE_HIGH_WATER_MARK
                : NetConstants.SERVICE_WRITE_HIGH_WATER_MARK;
        b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(low, high));
    }

    /**
     * 关闭服务器用
     */
    public void shutdown() {
        // 这里关闭的是你 bind(port) 返回的 ServerChannel（监听端口）, 关闭监听 Channel 不再接受新的玩家连接,
        // 已经连进来的玩家 SocketChannel,仍然保持连接，不会因为这个自动断开
        for (Channel channel1 : this.channel) {
            channel1.close();
        }
        // 会关闭 workerGroup 管理的 EventLoop。玩家连接的 SocketChannel 都注册在 workerGroup 里，所以在这个 Group 终止过程中，现有玩家连接会被关闭。
        // Netty 的说明是：shutdownGracefully() 完成后，该 Group 所属的 Channel 都已关闭。
        this.workerGroup.shutdownGracefully();
        //则是停止 boss 的 EventLoop 线程。你的监听 Channel 已经先 close() 了，所以它主要负责把 boss 线程和 selector/epoll 资源收掉；
        // 即使漏掉关闭监听 Channel，bossGroup 终止时也会把它关闭。
        this.bossGroup.shutdownGracefully();
    }

    public List<Channel> getChannelList() {
        return channel;
    }
}
