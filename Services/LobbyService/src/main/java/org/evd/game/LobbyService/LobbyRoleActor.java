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
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.SnowflakeIdGenerator;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;

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
            PlayerServiceProxy.inst().onPlayerOffline(
                    userState.getActivePlayerService(),
                    userState.getUserId(),
                    role.getPlayerId(),
                    BrokenType.LOGIN_REPLACE.getCode()
            );
        }

        LobbyPlayerCandidate playerCandidate = loadBalancerActor.selectLeastLoadedPlayer();
        if (playerCandidate == null) {
            pushSelectRoleResp(session, false, "找不到可用 PlayerService", 0L);
            return;
        }

        boolean bindSuccess = PlayerServiceProxy.inst().bindPlayerSession(
                playerCandidate.callPoint(),
                userState.getUserId(),
                role.getPlayerId(),
                session
        );
        if (!bindSuccess) {
            pushSelectRoleResp(session, false, "PlayerService 绑定失败", 0L);
            return;
        }

        PlayerServiceProxy.inst().enterMap(playerCandidate.callPoint(), role.getPlayerId());
        ConnServiceProxy.inst().updatePlayerBinding(session.getGate(), session.getSessionId(), role.getPlayerId());
        userState.setActivePlayerService(playerCandidate.callPoint());
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


    public void pushCreateRoleResp(ClientSessionRef session, boolean success, String message, RoleData role) {
        S2C_CreateRole.Builder builder = S2C_CreateRole.newBuilder()
                .setSuccess(success)
                .setMessage(message);
        if (role != null) {
            builder.setRole(role);
        }
        ConnServiceProxy.inst().pushToClient(session.getGate(), session.getSessionId(), MsgId.S2C_CREATE_ROLE_VALUE, new Chunk(builder.build()));
    }

    public void pushSelectRoleResp(ClientSessionRef session, boolean success, String message, long playerId) {
        S2C_SelectRoleEnter resp = S2C_SelectRoleEnter.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .setPlayerId(playerId)
                .build();
        ConnServiceProxy.inst().pushToClient(session.getGate(), session.getSessionId(), MsgId.S2C_SELECT_ROLE_ENTER_VALUE, new Chunk(resp));
    }
}
