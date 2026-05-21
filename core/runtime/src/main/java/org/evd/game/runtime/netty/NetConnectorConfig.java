package org.evd.game.runtime.netty;

public final class NetConnectorConfig {
    private final String host;
    private final int port;
    private final int workerThreads;
    private final int connectTimeoutMillis;

    public NetConnectorConfig(String host, int port) {
        this(host, port, 1, 1000);
    }

    public NetConnectorConfig(String host, int port, int workerThreads, int connectTimeoutMillis) {
        this.host = host;
        this.port = port;
        this.workerThreads = workerThreads;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }
}
