package org.evd.game.LobbyService;

import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.LobbyService.routing.LobbyPlayerCandidate;
import org.evd.game.LobbyService.session.LobbySessionRepository;
import org.evd.game.LobbyService.session.LobbyUserState;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.common.proto.*;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.util.id.SnowflakeIdGenerator;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.List;

@Actor
public final class LobbyRoleActor {
    @ClientCmd(MsgId.C2S_CREATE_ROLE_VALUE)
    public void createRole(ClientSessionRef session, C2S_CreateRole req) {
        LobbyService owner = owner();
        LobbySessionRepository sessionRepository = owner.sessionRepository();
        LobbyUserState userState = sessionRepository.findUser(session);
        if (userState == null) {
            pushCreateRoleResp(session, false, "未完成登录", null);
            return;
        }
        if (userState.getRole() != null) {
            pushCreateRoleResp(session, false, "角色已存在", userState.getRole().toProto());
            return;
        }

        String roleName = req.getName().trim();
        if (roleName.isEmpty()) {
            pushCreateRoleResp(session, false, "角色名不能为空", null);
            return;
        }

        LobbyRole role = new LobbyRole(SnowflakeIdGenerator.createPlayerId(), roleName, 1);
        userState.setRole(role);
        pushCreateRoleResp(session, true, "ok", role.toProto());
    }

    @ClientCmd(MsgId.C2S_SELECT_ROLE_ENTER_VALUE)
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter req) {
        LobbyService owner = owner();
        LobbySessionRepository sessionRepository = owner.sessionRepository();
        LobbyLoadBalancerActor loadBalancerActor = owner.loadBalancerActor();
        LobbyUserState userState = sessionRepository.findUser(session);
        if (userState == null) {
            pushSelectRoleResp(session, false, "未完成登录", 0L);
            return;
        }
        LobbyRole role = userState.getRole();
        if (role == null) {
            pushSelectRoleResp(session, false, "请先创建角色", 0L);
            return;
        }
        if (role.getPlayerId() != req.getPlayerId()) {
            pushSelectRoleResp(session, false, "角色不存在", 0L);
            return;
        }

        if (userState.getActivePlayerService() != null) {
            if (!clearActivePlayerService(userState, role.getPlayerId())) {
                pushSelectRoleResp(session, false, "旧角色下线失败", 0L);
                return;
            }
        }

        LobbyPlayerCandidate playerCandidate = loadBalancerActor.selectLeastLoadedPlayer();
        if (playerCandidate == null) {
            pushSelectRoleResp(session, false, "找不到可用 PlayerService", 0L);
            return;
        }

        RpcResult<Boolean> bindResult = PlayerServiceProxy.callBindPlayerSession(
                playerCandidate.callPoint(),
                userState.getUserId(),
                role.getPlayerId(),
                session
        );
        if (!bindResult.isSuccess()) {
            pushSelectRoleResp(session, false, "PlayerService 不可用", 0L);
            return;
        }
        if (!Boolean.TRUE.equals(bindResult.getValue())) {
            pushSelectRoleResp(session, false, "PlayerService 绑定失败", 0L);
            return;
        }
        userState.setActivePlayerService(playerCandidate.callPoint());

        RpcResult<Void> enterMapResult = PlayerServiceProxy.callEnterMap(playerCandidate.callPoint(), role.getPlayerId());
        if (!enterMapResult.isSuccess()) {
            clearActivePlayerService(userState, role.getPlayerId());
            pushSelectRoleResp(session, false, "PlayerService 进入地图失败", 0L);
            return;
        }
        RpcResult<Boolean> updateBindingResult = ConnServiceProxy.callUpdatePlayerBinding(
                session.getGate(), session.getSessionId(), role.getPlayerId());
        if (!updateBindingResult.isSuccess() || !Boolean.TRUE.equals(updateBindingResult.getValue())) {
            clearActivePlayerService(userState, role.getPlayerId());
            pushSelectRoleResp(session, false, "gate 玩家绑定失败", 0L);
            return;
        }
        pushSelectRoleResp(session, true, "ok", role.getPlayerId());
    }

    public static List<RoleData> buildRoleList(LobbyUserState userState) {
        if (userState == null || userState.getRole() == null) {
            return List.of();
        }
        List<RoleData> roleList = new ArrayList<>(1);
        roleList.add(userState.getRole().toProto());
        return roleList;
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }

    private boolean clearActivePlayerService(LobbyUserState userState, long playerId) {
        var playerService = userState.getActivePlayerService();
        if (playerService == null) {
            return true;
        }
        RpcResult<Void> offlineResult = PlayerServiceProxy.callOnPlayerOffline(
                playerService,
                userState.getUserId(),
                playerId,
                BrokenType.LOGIN_REPLACE.getCode()
        );
        if (!offlineResult.isSuccess()) {
            LogCore.core.warn("LobbyService 清理 PlayerService 玩家失败: userId={}, playerId={}, errorCode={}, message={}",
                    userState.getUserId(), playerId, offlineResult.getErrorCode(), offlineResult.getErrorMessage());
            return false;
        }
        userState.setActivePlayerService(null);
        return true;
    }


    public void pushCreateRoleResp(ClientSessionRef session, boolean success, String message, RoleData role) {
        S2C_CreateRole.Builder builder = S2C_CreateRole.newBuilder()
                .setSuccess(success)
                .setMessage(message);
        if (role != null) {
            builder.setRole(role);
        }
        RpcResult<Void> pushResult = ConnServiceProxy.callPushToClient(session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_CREATE_ROLE_VALUE, builder.build()));
        if (!pushResult.isSuccess()) {
            LogCore.core.warn("LobbyService 回创建角色响应失败: sessionId={}, errorCode={}, message={}",
                    session.getSessionId(), pushResult.getErrorCode(), pushResult.getErrorMessage());
        }
    }

    public void pushSelectRoleResp(ClientSessionRef session, boolean success, String message, long playerId) {
        S2C_SelectRoleEnter resp = S2C_SelectRoleEnter.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .setPlayerId(playerId)
                .build();
        RpcResult<Void> pushResult = ConnServiceProxy.callPushToClient(session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_SELECT_ROLE_ENTER_VALUE, resp));
        if (!pushResult.isSuccess()) {
            LogCore.core.warn("LobbyService 回选择角色响应失败: sessionId={}, errorCode={}, message={}",
                    session.getSessionId(), pushResult.getErrorCode(), pushResult.getErrorMessage());
        }
    }
}
