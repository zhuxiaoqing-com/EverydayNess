package org.evd.game.runtime.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import org.evd.game.runtime.Session;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class NettyClientTransport implements ClientTransport {
    private static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("client-session");

    private final NettyServerConfig config;
    private final ClientTransportHandler handler;
    private final AtomicLong nextSessionId = new AtomicLong(1L);
    private final ConcurrentMap<Long, Channel> channels = new ConcurrentHashMap<>();

    private volatile NioEventLoopGroup bossGroup;
    private volatile NioEventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    public NettyClientTransport(NettyServerConfig config, ClientTransportHandler handler) {
        this.config = Objects.requireNonNull(config, "config");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public synchronized void start() {
        if (serverChannel != null) {
            return;
        }

        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        int workerThreads = config.getWorkerThreads() > 0
                ? config.getWorkerThreads()
                : Runtime.getRuntime().availableProcessors();
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(config.getMaxFrameLength(), 0, 4, 0, 4))
                                    .addLast(new ClientFrameDecoder())
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new ClientFrameEncoder())
                                    .addLast(new ServerInboundHandler());
                        }
                    });
            serverChannel = bootstrap.bind(config.getHost(), config.getPort()).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
            stop();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        Channel channel = serverChannel;
        serverChannel = null;
        if (channel != null) {
            channel.close().awaitUninterruptibly();
        }

        for (Channel clientChannel : channels.values()) {
            clientChannel.close().awaitUninterruptibly();
        }
        channels.clear();

        NioEventLoopGroup currentWorker = workerGroup;
        workerGroup = null;
        if (currentWorker != null) {
            currentWorker.shutdownGracefully().awaitUninterruptibly();
        }

        NioEventLoopGroup currentBoss = bossGroup;
        bossGroup = null;
        if (currentBoss != null) {
            currentBoss.shutdownGracefully().awaitUninterruptibly();
        }
    }

    @Override
    public void send(long sessionId, int msgId, byte[] body) {
        Channel channel = channels.get(sessionId);
        if (channel == null) {
            throw new IllegalStateException("client session not found: sessionId=" + sessionId);
        }
        channel.writeAndFlush(new ClientFrame(msgId, body == null ? new byte[0] : Arrays.copyOf(body, body.length)));
    }

    private final class ServerInboundHandler extends SimpleChannelInboundHandler<ClientFrame> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Session session = new Session(nextSessionId.getAndIncrement(), remoteAddress(ctx.channel().remoteAddress()));
            ctx.channel().attr(SESSION_KEY).set(session);
            channels.put(session.getSessionId(), ctx.channel());
            handler.onConnected(session);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ClientFrame msg) {
            Session session = ctx.channel().attr(SESSION_KEY).get();
            if (session == null) {
                throw new IllegalStateException("netty session not initialized");
            }
            handler.onPacket(session, msg.msgId, msg.body);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            Session session = ctx.channel().attr(SESSION_KEY).getAndSet(null);
            if (session != null) {
                channels.remove(session.getSessionId(), ctx.channel());
                handler.onDisconnected(session);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            handler.onException(ctx.channel().attr(SESSION_KEY).get(), cause);
            ctx.close();
        }
    }

    private static final class ClientFrame {
        private final int msgId;
        private final byte[] body;

        private ClientFrame(int msgId, byte[] body) {
            this.msgId = msgId;
            this.body = body;
        }
    }

    private static final class ClientFrameDecoder extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            if (msg.readableBytes() < Integer.BYTES) {
                throw new IllegalStateException("client frame too short");
            }
            int msgId = msg.readInt();
            byte[] body = new byte[msg.readableBytes()];
            msg.readBytes(body);
            ctx.fireChannelRead(new ClientFrame(msgId, body));
        }
    }

    private static final class ClientFrameEncoder extends MessageToByteEncoder<ClientFrame> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ClientFrame msg, ByteBuf out) {
            out.writeInt(msg.msgId);
            out.writeBytes(msg.body);
        }
    }

    private static String remoteAddress(SocketAddress remoteAddress) {
        return remoteAddress == null ? "unknown" : remoteAddress.toString();
    }
}
