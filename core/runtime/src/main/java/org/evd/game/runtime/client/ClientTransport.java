package org.evd.game.runtime.client;

public interface ClientTransport extends AutoCloseable {
    void start();

    void stop();

    void send(long sessionId, int msgId, byte[] body);

    @Override
    default void close() {
        stop();
    }
}
