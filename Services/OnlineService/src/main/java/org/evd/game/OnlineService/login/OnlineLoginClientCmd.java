package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ClientCmdHandler;
import org.evd.game.common.proto.C2S_Login2;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** OnlineService 二段登录客户端命令入口。 */
@Actor
@ClientCmdHandler
public final class OnlineLoginClientCmd {
    @ClientCmd(MsgId.C2S_LOGIN2_VALUE)
    public void login2(ClientSessionRef session, C2S_Login2 request) {
        logic().login2(session, request);
    }

    private OnlineLoginLogic logic() {
        return Service.getCurrent(OnlineService.class).getActor(OnlineLoginLogic.class);
    }
}
