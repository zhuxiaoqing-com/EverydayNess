package org.evd.game.OnlineService.playerlogin;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlinePlayer;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.RoleData;
import org.evd.game.common.proto.S2C_SelectRoleEnter;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.LobbyService.LobbyServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.common.serializeBean.LobbyService.LobbyRoleSnapshot;
import org.evd.game.common.serializeBean.OnlineService.OnlinePlayerCandidate;
import org.evd.game.common.serializeBean.OnlineService.OnlineUserState;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.support.LogCore;

/** OnlineService 的选角登录入口，OnlinePlayer 是本流程的生命周期状态。 */
@Actor
public final class OnlinePlayerLoginActor {
    @Rpc
    public void selectRoleEnter(ClientSessionRef session, C2S_SelectRoleEnter request) {
        OnlineService owner = owner();
        String userId = request.getUserId().trim();
        long playerId = request.getPlayerId();
        if (userId.isEmpty() || playerId <= 0L || session.getGate() == null
                || session.getSessionId() <= 0L) {
            pushFailure(session, playerId, "选角参数非法");
            return;
        }

        OnlineSessionCoordinator sessionCoordinator = owner.sessionCoordinator();
        OnlineUserState userState = sessionCoordinator.getUserState(userId);
        if (!sessionCoordinator.matchesSession(userId, session.getGate(), session.getSessionId())) {
            LogCore.core.warn("OnlineService 选角请求 Session 已失效: userId={}, playerId={}, gate={}, gateSessionId={}, currentState={}",
                    userId, playerId, session.getGate(), session.getSessionId(), userState);
            return;
        }
        if (sessionCoordinator.getOnlinePlayer(userId) != null) {
            LogCore.core.warn("OnlineService 当前 Session 已经选择玩家，忽略重复选角: userId={}, playerId={}, gateSessionId={}, currentPlayer={}",
                    userId, playerId, session.getSessionId(), sessionCoordinator.getOnlinePlayer(userId));
            return;
        }

        LobbyRoleSnapshot role = loadRole(owner, userId);
        if (!isCurrentSession(owner, userId, session)) {
            LogCore.core.warn("OnlineService Lobby 返回后 Session 已失效: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }
        if (role == null) {
            if (sessionCoordinator.getOnlinePlayer(userId) != null) {
                LogCore.core.warn("OnlineService Lobby 加载角色失败，但玩家已被其他流程登记: userId={}, playerId={}, gateSessionId={}",
                        userId, playerId, session.getSessionId());
                return;
            }
            LogCore.core.warn("OnlineService Lobby 加载角色失败，踢出客户端: userId={}, playerId={}, gate={}, gateSessionId={}",
                    userId, playerId, session.getGate(), session.getSessionId());
            kickSession(session, "LobbyService 加载玩家失败");
            return;
        }
        if (role.getPlayerId() != playerId) {
            pushFailure(session, playerId, "角色不存在或不属于当前账号");
            return;
        }

        OnlinePlayerCandidate candidate = owner.serviceSelector()
                .selectLeastLoadedPlayer(userId);
        CallPoint playerService = candidate == null ? null : candidate.getCallPoint();
        if (playerService == null) {
            LogCore.core.warn("OnlineService 选择 PlayerService 前 Session 已失效或无可用服务: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            if (isCurrentSession(owner, userId, session)) {
                kickSession(session, "找不到可用 PlayerService");
            }
            return;
        }

        // 先登记 OnlinePlayer，再调用 PlayerService；断线时 Online 可以直接找到并回滚它。
        OnlinePlayer onlinePlayer = sessionCoordinator.beginOnlinePlayer(
                userId, session.getGate(), session.getSessionId(), playerId, playerService);
        if (onlinePlayer == null) {
            LogCore.core.warn("OnlineService 玩家已经被其他上线流程登记: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        RoleData roleData = RoleData.newBuilder()
                .setPlayerId(role.getPlayerId())
                .setName(role.getName())
                .setLevel(role.getLevel())
                .setCharacterId(role.getCharacterId())
                .build();
        RpcResult<ActorAddress> playerLogin = PlayerServiceProxy.callLoginPlayer(
                playerService, userId, roleData, session);
        ActorAddress playerActorAddress = playerLogin.getValue();
        if (!playerLogin.isSuccess() || playerActorAddress == null) {
            LogCore.core.warn("OnlineService PlayerService 玩家登录失败: userId={}, playerId={}, playerService={}, errorCode={}, message={}, value={}",
                    userId, playerId, playerService, playerLogin.getErrorCode(),
                    playerLogin.getErrorMessage(), playerLogin.getValue());
            if (isCurrentSession(owner, userId, session)) {
                kickSession(session, "PlayerService 加载玩家失败");
            } else {
                LogCore.core.warn("OnlineService PlayerService 登录失败时 Session 已失效，等待离线流程清理: userId={}, playerId={}, gateSessionId={}",
                        userId, playerId, session.getSessionId());
            }
            return;
        }

        if (!sessionCoordinator.bindPlayerActorAddress(onlinePlayer, playerActorAddress)) {
            LogCore.core.warn("OnlineService PlayerService 返回后 OnlinePlayer 已由离线流程清理: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        RpcResult<ActorAddress> gateBound = ConnServiceProxy.callBindPlayer(
                session.getGate(), session.getSessionId(), playerId, playerActorAddress);
        ActorAddress gateActorAddress = gateBound.getValue();
        if (!gateBound.isSuccess() || gateActorAddress == null) {
            LogCore.core.warn("OnlineService 网关绑定玩家失败，踢出当前 Session: userId={}, playerId={}, gate={}, gateSessionId={}, errorCode={}, message={}, value={}",
                    userId, playerId, session.getGate(), session.getSessionId(),
                    gateBound.getErrorCode(), gateBound.getErrorMessage(), gateBound.getValue());
            kickSession(session, "网关绑定玩家失败");
            return;
        }
        if (!sessionCoordinator.bindGateActorAddress(onlinePlayer, gateActorAddress)) {
            LogCore.core.warn("OnlineService GW 返回后绑定 GWActorAddress 失败，踢出当前 Session: userId={}, playerId={}, gate={}, gateSessionId={}",
                    userId, playerId, session.getGate(), session.getSessionId());
            kickSession(session, "网关玩家地址绑定失败");
            return;
        }

        if (!sessionCoordinator.markPlayerOnline(onlinePlayer)) {
            LogCore.core.warn("OnlineService 标记玩家正式在线失败: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        RpcResult<Void> playerOnline = PlayerServiceProxy.sendOnlinePlayer(
                playerService, userId, playerId, session, gateActorAddress);
        if (!playerOnline.isSuccess()) {
            LogCore.core.warn("OnlineService 发送 PlayerService 正式上线失败: userId={}, playerId={}, playerService={}, errorCode={}, message={}",
                    userId, playerId, playerService,
                    playerOnline.getErrorCode(), playerOnline.getErrorMessage());
        }

        CallPoint lobby = owner.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        if (lobby == null) {
            LogCore.core.warn("OnlineService 发送 LobbyService 正式上线失败，服务不存在: userId={}, playerId={}",
                    userId, playerId);
        } else {
            RpcResult<Void> lobbyOnline = LobbyServiceProxy.sendPlayerOnline(
                    lobby, userId, playerId, session.getGate(), session.getSessionId());
            if (!lobbyOnline.isSuccess()) {
                LogCore.core.warn("OnlineService 发送 LobbyService 正式上线失败: userId={}, playerId={}, errorCode={}, message={}",
                        userId, playerId, lobbyOnline.getErrorCode(), lobbyOnline.getErrorMessage());
            }
        }

        LogCore.core.info("OnlineService 玩家登录成功: userId={}, playerId={}, gate={}, gateSessionId={}, playerService={}, status={}",
                userId, playerId, session.getGate(), session.getSessionId(),
                playerService, onlinePlayer.getStatus());
        pushSuccess(session, playerId);
    }

    private LobbyRoleSnapshot loadRole(OnlineService owner, String userId) {
        CallPoint lobby = owner.getNode().getAnyCallPointByType(ServiceType.LOBBY);
        if (lobby == null) {
            return null;
        }
        RpcResult<LobbyRoleSnapshot> result = LobbyServiceProxy.callGetRole(lobby, userId);
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 查询角色失败: userId={}, errorCode={}, message={} ",
                    userId, result.getErrorCode(), result.getErrorMessage());
            return null;
        }
        return result.getValue();
    }

    private void pushSuccess(ClientSessionRef session, long playerId) {
        push(session, S2C_SelectRoleEnter.newBuilder()
                .setSuccess(true).setMessage("ok").setPlayerId(playerId).build());
    }

    private void pushFailure(ClientSessionRef session, long playerId, String reason) {
        LogCore.core.warn("OnlineService 选角登录失败: gate={}, gateSessionId={}, playerId={}, reason={}",
                session.getGate(), session.getSessionId(), playerId, reason);
        push(session, S2C_SelectRoleEnter.newBuilder()
                .setSuccess(false).setMessage(reason).setPlayerId(playerId).build());
    }

    /** 检查 OnlineUserState 是否仍对应本次选角请求。 */
    private boolean isCurrentSession(OnlineService owner, String userId, ClientSessionRef session) {
        return owner.sessionCoordinator().matchesSession(
                userId, session.getGate(), session.getSessionId());
    }

    private void kickSession(ClientSessionRef session, String reason) {
        RpcResult<Void> result = ConnServiceProxy.sendCloseSession(
                session.getGate(), session.getSessionId(),
                BrokenType.SERVER_KICK.getCode(), reason);
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 关闭选角失败连接失败: gate={}, gateSessionId={}, reason={}, errorCode={}, message={}",
                    session.getGate(), session.getSessionId(), reason,
                    result.getErrorCode(), result.getErrorMessage());
        }
    }

    private void push(ClientSessionRef session, S2C_SelectRoleEnter response) {
        RpcResult<Void> result = ConnServiceProxy.sendPushToClient(
                session.getGate(), session.getSessionId(),
                ClientFrameChunk.wrap(MsgId.S2C_SELECT_ROLE_ENTER_VALUE, response));
        if (!result.isSuccess()) {
            LogCore.core.warn("OnlineService 回选角响应失败: gateSessionId={}, errorCode={}, message={}",
                    session.getSessionId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    private OnlineService owner() {
        return Service.getCurrent(OnlineService.class);
    }
}
