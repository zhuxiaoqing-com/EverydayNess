package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.ClientCmd;
import org.evd.game.annotation.actor.ClientCmdHandler;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.AuthMsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** ConnService 首段登录客户端命令入口。 */
@Actor
@ClientCmdHandler
public final class ConnLoginClientCmd {
    @ClientCmd(AuthMsgId.C2S_AUTH_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login request) {
        Service.getCurrent(ConnService.class).getActor(ConnLoginLogic.class).login(session, request);
    }
}
