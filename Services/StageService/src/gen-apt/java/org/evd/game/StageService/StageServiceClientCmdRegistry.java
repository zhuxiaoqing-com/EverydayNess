package org.evd.game.StageService;

import com.google.protobuf.InvalidProtocolBufferException;
import org.evd.game.runtime.client.ClientCmdRegistryBase;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.C2S_Login2;

/**
 * 根据StageService生成的客户端协议分发类
 */
public final class StageServiceClientCmdRegistry extends ClientCmdRegistryBase<StageService> {
    private final HaHaHaActor haHaHaActor = new HaHaHaActor();

    public StageServiceClientCmdRegistry(StageService owner) {
        super(owner);
    }

    @Override
    public void dispatch(ClientSessionRef session, int cmd, byte[] body) throws InvalidProtocolBufferException {
        switch (cmd) {
            case 1001:
                owner().login(session, C2S_Login.parseFrom(body));
                return;
            case 1004:
                haHaHaActor.client(session, C2S_Login2.parseFrom(body));
                return;
            default:
                throw new IllegalArgumentException("unknown client cmd: " + cmd);
        }
    }
}
