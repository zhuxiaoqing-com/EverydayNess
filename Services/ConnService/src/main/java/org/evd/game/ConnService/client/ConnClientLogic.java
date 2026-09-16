package org.evd.game.ConnService.client;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proto.AuthMsgId;
import org.evd.game.common.proto.C2S_ConnPing;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.common.proto.S2C_ConnPing;
import org.evd.game.common.proxy.LobbyService.LobbyRoleRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlinePlayerLoginRpcProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.NetChannel;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/** ConnService 客户端协议的业务逻辑。 */
@Actor
public final class ConnClientLogic {
    /** Conn 从真实连接补充用户 ID，再交给 LobbyService 创建角色。 */
    public void createRole(ClientSessionRef session, C2S_CreateRole request) {
        ConnService owner = owner();
        NetChannel channel = owner.clientConnection().requireClientChannel(session.getSessionId());
        String userId = channel.getUserId();
        if (userId.isBlank()) {
            throw new IllegalStateException("ConnService 创角请求没有已登录用户: sessionId="
                    + session.getSessionId());
        }
        CallPoint lobby = owner.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        if (lobby == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: service=LobbyService, msgId="
                    + AuthMsgId.C2S_AUTH_CREATE_ROLE_VALUE);
        }
        C2S_CreateRole forwarded = request.toBuilder().setUserId(userId).build();
        LobbyRoleRpcProxy.sendCreateRole(lobby, session, forwarded);
    }

    /** Conn 从真实连接补充用户 ID，再交给 OnlineService 选角登录。 */
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter request) {
        ConnService owner = owner();
        NetChannel channel = owner.clientConnection().requireClientChannel(session.getSessionId());
        String userId = channel.getUserId();
        if (userId.isBlank()) {
            throw new IllegalStateException("ConnService 选角请求没有已登录用户: sessionId="
                    + session.getSessionId());
        }
        CallPoint online = owner.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        if (online == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: service=OnlineService, msgId="
                    + AuthMsgId.C2S_AUTH_SELECT_ROLE_ENTER_VALUE);
        }
        C2S_SelectRoleEnter forwarded = request.toBuilder().setUserId(userId).build();
        OnlinePlayerLoginRpcProxy.sendSelectRoleEnter(online, session, forwarded);
    }

    public void onConnPing(ClientSessionRef session, C2S_ConnPing req) {
        ConnService owner = owner();
        NetChannel channel = owner.clientConnection().findClientChannel(session.getSessionId());
        if (channel != null) {
            channel.setLastPingTime(owner.getTimeCurrent());
        }

        S2C_ConnPing resp = S2C_ConnPing.newBuilder()
                .setClientTime(req.getClientTime())
                .setServerTime(ConnService.getTime())
                .build();
        owner.clientConnection().pushToClient(
                session.getSessionId(), ClientFrameChunk.wrap(AuthMsgId.S2C_AUTH_CONN_PING_VALUE, resp));
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
