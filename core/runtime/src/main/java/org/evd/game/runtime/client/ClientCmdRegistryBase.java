package org.evd.game.runtime.client;

import com.google.protobuf.InvalidProtocolBufferException;
import org.evd.game.runtime.Service;

public abstract class ClientCmdRegistryBase<T extends Service> {
    private final T owner;

    protected ClientCmdRegistryBase(T owner) {
        this.owner = owner;
    }

    protected final T owner() {
        return owner;
    }

    public abstract void dispatch(ClientSessionRef session, int cmd, byte[] body)
            throws InvalidProtocolBufferException;
}
