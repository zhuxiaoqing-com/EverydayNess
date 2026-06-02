package org.evd.game.StageService;

import com.google.protobuf.InvalidProtocolBufferException;
import org.evd.game.common.proto.MsgId;
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
            case MsgId.C2S_LOGIN_VALUE:
                owner().login(session, C2S_Login.parseFrom(body));
                return;
            case MsgId.C2S_LOGIN3_VALUE:
                haHaHaActor.client(session, C2S_Login2.parseFrom(body));
                return;
            default:
                throw new IllegalArgumentException("unknown client cmd: " + cmd);
        }
    }
}
