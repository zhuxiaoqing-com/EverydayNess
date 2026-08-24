package org.evd.game.OnlineService.login;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.OnlineService.session.OnlinePlayer;
import org.evd.game.OnlineService.session.OnlineSessionCoordinator;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.proto.C2S_SelectRoleEnter;
import org.evd.game.common.proto.MsgId;
import org.evd.game.common.proto.RoleData;
import org.evd.game.common.proto.S2C_SelectRoleEnter;
import org.evd.game.common.proxy.ConnService.ConnLoginActorProxy;
import org.evd.game.common.proxy.ConnService.ConnOfflineActorProxy;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.LobbyService.LobbyServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerLoginRpcActorProxy;
import org.evd.game.common.serializeBean.LobbyService.role.LobbyRoleSnapshot;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;
import org.evd.game.common.serializeBean.OnlineService.session.OnlineUserState;
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

        // 1. 先确认当前连接仍是 Online 记录的有效会话，并拒绝重复选角。
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

        // 2. 从 LobbyService 查询并校验当前账号要进入的角色。
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

        // 3. 选择承载该玩家的 PlayerService，优先使用当前负载较低的服务。
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

        // 4. 先登记 OnlinePlayer，再调用 PlayerService；断线时 Online 可以直接找到并回滚它。
        OnlinePlayer onlinePlayer = sessionCoordinator.beginOnlinePlayer(
                userId, session.getGate(), session.getSessionId(), playerId, playerService);
        if (onlinePlayer == null) {
            LogCore.core.warn("OnlineService 玩家已经被其他上线流程登记: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        // 5. 请求 PlayerService 创建玩家 Actor、加载玩家数据并返回 Player ActorAddress。
        RoleData roleData = RoleData.newBuilder()
                .setPlayerId(role.getPlayerId())
                .setName(role.getName())
                .setLevel(role.getLevel())
                .setCharacterId(role.getCharacterId())
                .build();
        RpcResult<ActorAddress> playerLogin = PlayerLoginRpcActorProxy.callLoginPlayer(
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

        // 异步 RPC 返回后再次确认 Session，旧登录流程不能继续写入新会话状态。
        if (!isCurrentSession(owner, userId, session)) {
            LogCore.core.warn("OnlineService PlayerService 玩家登录返回后 Session 已失效: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }
        if (!sessionCoordinator.bindPlayerActorAddress(onlinePlayer, playerActorAddress)) {
            LogCore.core.warn("OnlineService PlayerService 返回后 OnlinePlayer 已由离线流程清理: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        // 6. 将 Player Actor 绑定到当前 GW，建立客户端消息到玩家 Actor 的路由。
        RpcResult<ActorAddress> gateBound = ConnLoginActorProxy.callBindPlayer(
                session.getGate(), session.getSessionId(), playerId, playerActorAddress);
        ActorAddress gateActorAddress = gateBound.getValue();
        if (!gateBound.isSuccess() || gateActorAddress == null) {
            LogCore.core.warn("OnlineService 网关绑定玩家失败，踢出当前 Session: userId={}, playerId={}, gate={}, gateSessionId={}, errorCode={}, message={}, value={}",
                    userId, playerId, session.getGate(), session.getSessionId(),
                    gateBound.getErrorCode(), gateBound.getErrorMessage(), gateBound.getValue());
            kickSession(session, "网关绑定玩家失败");
            return;
        }
        if (!isCurrentSession(owner, userId, session)) {
            LogCore.core.warn("OnlineService Conn 绑定玩家返回后 Session 已失效: userId={}, playerId={}, gateSessionId={}",
                    userId, playerId, session.getSessionId());
            return;
        }

        // 下面都是send 没有让出协程，所以不需要继续判断isCurrentSession;
        // 7. 将 GW ActorAddress 同步到 Online 和 PlayerService 两侧。
        sessionCoordinator.bindGateActorAddress(onlinePlayer, gateActorAddress);
        RpcResult<Void> gatePlayerBound = PlayerLoginRpcActorProxy.sendBindGateActorAddress(
                playerService, playerId, gateActorAddress);
        if (!gatePlayerBound.isSuccess()) {
            LogCore.core.warn("OnlineService 发送 PlayerService 绑定 GW 玩家地址通知失败: userId={}, playerId={}, playerService={}, errorCode={}, message={}",
                    userId, playerId, playerService,
                    gatePlayerBound.getErrorCode(), gatePlayerBound.getErrorMessage());
        }

        //  PlayerService 通知已发出后，直接标记 OnlinePlayer 正式在线。
        onlinePlayer.markOnline();

        //  通知 LobbyService 当前角色已进入游戏。
        RpcResult<Void> lobbyOnline = LobbyServiceProxy.sendPlayerOnline(
                null, userId, playerId, session.getGate(), session.getSessionId());
        if (!lobbyOnline.isSuccess()) {
            LogCore.core.warn("OnlineService 发送 LobbyService 正式上线失败: userId={}, playerId={}, errorCode={}, message={}",
                    userId, playerId, lobbyOnline.getErrorCode(), lobbyOnline.getErrorMessage());
        }


        //  通知 PlayerService 执行进入地图和本地正式上线处理。
        RpcResult<Void> playerOnline = PlayerLoginRpcActorProxy.sendOnlinePlayer(
                playerService, userId, playerId, session);
        if (!playerOnline.isSuccess()) {
            LogCore.core.warn("OnlineService 发送 PlayerService 正式上线通知失败: userId={}, playerId={}, playerService={}, errorCode={}, message={}",
                    userId, playerId, playerService,
                    playerOnline.getErrorCode(), playerOnline.getErrorMessage());
        }


        //  所有上线步骤完成后，向客户端返回选角进入成功。
        LogCore.core.info("OnlineService 玩家登录成功: userId={}, playerId={}, gate={}, gateSessionId={}, playerService={}, status={}",
                userId, playerId, session.getGate(), session.getSessionId(),
                playerService, onlinePlayer.getStatus());
        pushSuccess(session, playerId);
    }

    private LobbyRoleSnapshot loadRole(OnlineService owner, String userId) {
        RpcResult<LobbyRoleSnapshot> result = LobbyServiceProxy.callGetRole(null, userId);
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
        RpcResult<Void> result = ConnOfflineActorProxy.sendCloseSession(
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
