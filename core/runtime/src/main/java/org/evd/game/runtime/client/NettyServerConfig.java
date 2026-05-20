package org.evd.game.runtime.client;

public final class NettyServerConfig {
    private final String host;
    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    private final int maxFrameLength;

    public NettyServerConfig(String host, int port) {
        this(host, port, 1, 0, 8 * 1024 * 1024);
    }

    public NettyServerConfig(String host, int port, int bossThreads, int workerThreads, int maxFrameLength) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("netty host is blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("netty port must be positive: " + port);
        }
        if (bossThreads <= 0) {
            throw new IllegalArgumentException("bossThreads must be positive: " + bossThreads);
        }
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be positive: " + maxFrameLength);
        }
        this.host = host;
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.maxFrameLength = maxFrameLength;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }
}
