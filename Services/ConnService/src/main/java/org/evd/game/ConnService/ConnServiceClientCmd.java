package org.evd.game.ConnService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.ClientCmdHandler;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.client.ClientSessionRef;

/** ConnService 客户端命令入口。 */
@Actor
@ClientCmdHandler
public final class ConnServiceClientCmd {
    @ClientCmd(MsgId.C2S_CREATE_ROLE_VALUE)
    public void createRole(ClientSessionRef session, C2S_CreateRole request) {
        owner().createRole(session, request);
    }

    @ClientCmd(MsgId.C2S_SELECT_ROLE_ENTER_VALUE)
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter request) {
        owner().selectRoleEnter(session, request);
    }

    @ClientCmd(MsgId.C2S_CONN_PING_VALUE)
    public void onConnPing(ClientSessionRef session, C2S_ConnPing request) {
        owner().onConnPing(session, request);
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
