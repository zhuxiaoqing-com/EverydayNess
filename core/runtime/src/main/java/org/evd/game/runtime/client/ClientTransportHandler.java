package org.evd.game.runtime.client;

import org.evd.game.runtime.Session;

public interface ClientTransportHandler {
    void onConnected(Session session);

    void onDisconnected(Session session);

    void onPacket(Session session, int msgId, byte[] body);

    void onException(Session session, Throwable cause);
}
