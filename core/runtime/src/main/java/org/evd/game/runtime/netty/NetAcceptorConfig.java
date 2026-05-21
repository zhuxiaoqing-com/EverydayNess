package org.evd.game.runtime.netty;

public final class NetAcceptorConfig {
    private final String host;
    private final int port;
    private final int bossThreads;
    private final int workerThreads;

    public NetAcceptorConfig(String host, int port) {
        this(host, port, 1, 0);
    }

    public NetAcceptorConfig(String host, int port, int bossThreads, int workerThreads) {
        this.host = host;
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
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
}
