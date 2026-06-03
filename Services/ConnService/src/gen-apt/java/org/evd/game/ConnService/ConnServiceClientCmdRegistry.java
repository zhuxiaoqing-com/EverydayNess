package org.evd.game.ConnService;

import com.google.protobuf.InvalidProtocolBufferException;
import org.evd.game.runtime.client.ClientCmdRegistryBase;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.common.proto.C2S_ConnPing;

/**
 * 根据ConnService生成的客户端协议分发类
 */
public final class ConnServiceClientCmdRegistry extends ClientCmdRegistryBase<ConnService> {
    public ConnServiceClientCmdRegistry(ConnService owner) {
        super(owner);
    }

    @Override
    public void dispatch(ClientSessionRef session, int cmd, byte[] body) throws InvalidProtocolBufferException {
        switch (cmd) {
            case 1002:
                owner().onConnPing(session, C2S_ConnPing.parseFrom(body));
                return;
            default:
                throw new IllegalArgumentException("unknown client cmd: " + cmd);
        }
    }
}
