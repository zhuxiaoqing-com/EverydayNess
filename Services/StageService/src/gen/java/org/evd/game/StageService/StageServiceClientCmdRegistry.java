package org.evd.game.StageService;

import com.google.protobuf.InvalidProtocolBufferException;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.ClientSessionRef;
import org.evd.game.common.proto.C2S_Login;

/**
 * 根据StageService生成的客户端协议分发类
 */
public final class StageServiceClientCmdRegistry {
    private final StageService owner;

    public StageServiceClientCmdRegistry(StageService owner) {
        this.owner = owner;
    }

    public void dispatch(ClientSessionRef session, int cmd, byte[] body) throws InvalidProtocolBufferException {
        switch (cmd) {
            case MsgId.C2S_LOGIN_VALUE:
                owner.login(session, C2S_Login.parseFrom(body));
                return;
            default:
                throw new IllegalArgumentException("unknown client cmd: " + cmd);
        }
    }
}
