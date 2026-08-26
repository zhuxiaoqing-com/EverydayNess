package org.evd.game.LobbyService;

import org.evd.game.LobbyService.account.LobbyUserAccountRepository;
import org.evd.game.LobbyService.dbDef.db.bean.LBRole;
import org.evd.game.LobbyService.dbDef.db.bean.LBUserAccount;
import org.evd.game.annotation.Actor;
import org.evd.game.common.proto.C2S_CreateRole;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.RoleData;
import org.evd.game.common.proto.S2C_CreateRole;
import org.evd.game.common.proto.S2C_RoleList;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.util.id.SnowflakeIdGenerator;

import java.util.List;

/** Lobby 角色业务逻辑。 */
@Actor
public final class LobbyRoleLogic {
    public void createRole(ClientSessionRef session, C2S_CreateRole req) {
        LobbyService owner = owner();
        String userId = req.getUserId().trim();
        LobbyUserAccountRepository accountRepository = owner.userAccountRepository();
        LBUserAccount account = accountRepository.findAccount(userId);
        if (account == null || account.getStatus() != LobbyUserAccountRepository.STATUS_NORMAL) {
            rejectCreate(session, "用户账号不存在或不可用", null);
            return;
        }
        if (!account.getPlayerIds().isEmpty()) {
            LBRole existingRole = accountRepository.loadRole(account.getPlayerIds().get(0));
            rejectCreate(session, "角色已存在", toRoleData(existingRole));
            return;
        }
        String roleName = req.getName().trim();
        if (roleName.isEmpty()) {
            rejectCreate(session, "角色名不能为空", null);
            return;
        }
        int characterId = req.getCharacterId();
        if (characterId <= 0) {
            rejectCreate(session, "角色配置非法", null);
            return;
        }

        long playerId = SnowflakeIdGenerator.createPlayerId();
        if (!accountRepository.createRole(userId, playerId, characterId, roleName, 1)) {
            rejectCreate(session, "用户角色数据保存失败", null);
            return;
        }
        LogCore.core.info("LobbyService 创建角色成功: service={}, userId={}, gateSessionId={}, playerId={}",
                owner.getId(), userId, session.getSessionId(), playerId);
        pushCreateRoleResp(session, true, "ok",
                toRoleData(playerId, characterId, roleName, 1));
        pushRoleList(session.getGate(), session.getSessionId(), userId);
    }

    public void roleList(CallPoint gate, long gateSessionId, String userId) {
        pushRoleList(gate, gateSessionId, userId);
    }

    private void rejectCreate(ClientSessionRef session, String reason, RoleData role) {
        LogCore.core.info("LobbyService 创建角色拒绝: gateSessionId={}, reason={}",
                session.getSessionId(), reason);
        pushCreateRoleResp(session, false, reason, role);
    }

    private void pushCreateRoleResp(ClientSessionRef session, boolean success,
                                    String message, RoleData role) {
        S2C_CreateRole.Builder builder = S2C_CreateRole.newBuilder()
                .setSuccess(success)
                .setMessage(message);
        if (role != null) {
            builder.setRole(role);
        }
        RpcResult<Void> result = ConnServiceRpcProxy.sendPushToClient(
                session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_CREATE_ROLE_VALUE, builder.build()));
        if (!result.isSuccess()) {
            LogCore.core.warn("LobbyService 回创建角色响应失败: gateSessionId={}, errorCode={}, message={}",
                    session.getSessionId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    private static RoleData toRoleData(LBRole role) {
        return role == null ? null
                : toRoleData(role.getPlayerId(), role.getCharacterId(), role.getName(), role.getLevel());
    }

    private static RoleData toRoleData(long playerId, int characterId, String name, int level) {
        return RoleData.newBuilder()
                .setPlayerId(playerId)
                .setCharacterId(characterId)
                .setName(name)
                .setLevel(level)
                .build();
    }

    private void pushRoleList(CallPoint gate, long gateSessionId, String userId) {
        LobbyUserAccountRepository accountRepository = owner().userAccountRepository();
        LBRole role = accountRepository.loadRole(userId);
        List<RoleData> roles = role == null ? List.of() : List.of(toRoleData(role));
        S2C_RoleList response = S2C_RoleList.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .addAllRoles(roles)
                .build();
        RpcResult<Void> result = ConnServiceRpcProxy.sendPushToClient(
                gate, gateSessionId, ClientFrameChunk.wrap(MsgId.S2C_ROLE_LIST_VALUE, response));
        if (!result.isSuccess()) {
            LogCore.core.warn("LobbyService 回角色列表失败: userId={}, gateSessionId={}, errorCode={}, message={}",
                    userId, gateSessionId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
