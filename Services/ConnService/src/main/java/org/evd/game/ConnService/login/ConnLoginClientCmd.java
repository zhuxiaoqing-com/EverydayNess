package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ClientCmdHandler;
import org.evd.game.common.proto.C2S_Login;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** ConnService 首段登录客户端命令入口。 */
@Actor
@ClientCmdHandler
public final class ConnLoginClientCmd {
    @ClientCmd(MsgId.C2S_LOGIN_VALUE)
    public void login(ClientSessionRef session, C2S_Login request) {
        Service.getCurrent(ConnService.class).getActor(ConnLoginLogic.class).login(session, request);
    }
}
