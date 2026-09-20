package org.evd.game.PlayerService.disconnect;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.dbDef.db.bean.DBMatchContext;
import org.evd.game.PlayerService.dbDef.db.bean.DBRoleMapData;
import org.evd.game.PlayerService.dbDef.db.table.DBRoleMapDataTable;
import org.evd.game.PlayerService.map.PlayerMapLogic;
import org.evd.game.PlayerService.map.PlayerMapState;
import org.evd.game.PlayerService.map.PlayerMapStateLogic;
import org.evd.game.PlayerService.offline.PlayerOfflineLogic;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.constant.MatchConst;
import org.evd.game.common.proto.MatchMsgId;
import org.evd.game.common.proto.S2C_CancelMatch;
import org.evd.game.common.proxy.ConnService.ConnServiceRpcProxy;
import org.evd.game.common.proxy.OnlineService.OnlineOfflineRpcProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;
import org.evd.game.runtime.netty.BrokenType;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;

/** PlayerService 关联服务断开后的玩家状态收敛逻辑。 */
@Actor
public final class PlayerServiceDisconnectLogic {
    /** 按关联服务类型分发 PlayerService 的断开清理。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service == null || service.getServiceType() == null) {
                continue;
            }
            switch (service.getServiceType()) {
                case CONN -> onConnServiceDisconnect(service);
                case ONLINE -> onOnlineServiceDisconnect();
                case STAGE -> onStageServiceDisconnect(service);
                case MATCH -> onMatchServiceDisconnect(service);
                default -> {
                }
            }
        }
    }

    /** 关联 GW 断开时，只清理归属于该 GW 的当前会话。 */
    private void onConnServiceDisconnect(RegisteredService service) {
        CallPoint callPoint = service.getCallPoint();
        if (callPoint == null) {
            return;
        }
        PlayerSessionManager sessionManager = owner().sessionManager();
        int matched = 0;
        for (PPlayerOnline online : new ArrayList<>(sessionManager.onlinePlayers())) {
            if (callPoint.equals(online.getGate())) {
                matched++;
                Service.launchCurrentCoroutine(
                        () -> {
                            notifyOnlineOffline(online);
                            offline().onPlayerOffline(online.getUserId(), online.getPlayerId(), online.getGate(),
                                    online.getGateSessionId(), BrokenType.SERVICE_DISCONNECT.getCode());
                        });

            }
        }
        LogCore.core.info("PlayerService 完成 ConnService 断开清理: service={}, connService={}, affected={}",
                owner().getId(), callPoint, matched);
    }

    /** GW 断开时通知 Online 删除对应的在线状态；Online 没有该状态时由远端直接忽略。 */
    private void notifyOnlineOffline(PPlayerOnline online) {
        RpcResult<Void> result = OnlineOfflineRpcProxy.sendOnSessionOffline(
                null, online.getUserId(), online.getPlayerId(), online.getGate(),
                online.getGateSessionId(), BrokenType.SERVICE_DISCONNECT.getCode());
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 通知 Online 清理 GW 断开玩家在线状态失败: service={}, userId={}, playerId={}, gate={}, gateSessionId={}, errorCode={}, message={}",
                    owner().getId(), online.getUserId(), online.getPlayerId(), online.getGate(),
                    online.getGateSessionId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    /** OnlineService 断开时，当前 PlayerService 的所有会话都必须本地收敛。 */
    private void onOnlineServiceDisconnect() {
        PlayerSessionManager sessionManager = owner().sessionManager();
        int affected = sessionManager.getOnlineCount();
        for (PPlayerOnline online : new ArrayList<>(sessionManager.onlinePlayers())) {
            Service.launchCurrentCoroutine(() ->
                    offline().onPlayerOffline(online.getUserId(), online.getPlayerId(), online.getGate(),
                            online.getGateSessionId(), BrokenType.SERVICE_DISCONNECT.getCode())
            );
        }
        LogCore.core.info("PlayerService 完成 OnlineService 断开清理: service={}, affected={}",
                owner().getId(), affected);
    }

    /** Stage 断开时，直接踢出当前由该 Stage 承载的玩家。 */
    private void onStageServiceDisconnect(RegisteredService stageService) {
        if (stageService == null) {
            return;
        }
        CallPoint callPoint = stageService.getCallPoint();
        int affected = 0;
        ArrayList<PPlayerOnline> affectedPlayers = new ArrayList<>();
        for (PPlayerOnline online : owner().sessionManager().onlinePlayers()) {
            ActorAddress stageAddress = owner().getMessageLocationSender()
                    .get(ActorId.mapPlayer(online.getPlayerId()));
            if (stageAddress != null && callPoint.equals(stageAddress.getCallPoint())) {
                affectedPlayers.add(online);
            }
        }
        for (PPlayerOnline online : affectedPlayers) {
            LogCore.core.warn("PlayerService StageService 断开，踢出玩家: userId={}, playerId={}, stageService={}",
                    online.getUserId(), online.getPlayerId(), callPoint);
            offline().kickPlayer(online.getPlayerId(), "StageService 断开连接");
            affected++;
        }
        LogCore.core.info("PlayerService 完成 StageService 断开清理: service={}, stageService={}, affected={}",
                owner().getId(), callPoint, affected);
    }

    /** MatchService 断开时，清理本地匹配状态并通知客户端取消匹配。 */
    private void onMatchServiceDisconnect(RegisteredService service) {
        if (!service.getCallPoint().equals(MatchConst.getMatchCallPoint())) {
            return;
        }
        PlayerMapStateLogic stateLogic = owner().getActor(PlayerMapStateLogic.class);
        int affected = 0;
        ArrayList<PPlayerOnline> removeList = new ArrayList<>();
        for (PPlayerOnline player : owner().sessionManager().onlinePlayers()) {
            long playerId = player.getPlayerId();
            if (isMatchingState(stateLogic.getState(playerId))
                    || isActiveMatch(getMatchContext(playerId))) {
                removeList.add(player);
            }
        }
        for (PPlayerOnline player : removeList) {
            long playerId = player.getPlayerId();
            LogCore.core.info("PlayerService MatchService 断开清理玩家匹配: userId={}, playerId={}, state={}, matchContext={}",
                    player.getUserId(), playerId, stateLogic.getState(playerId), getMatchContext(playerId));
            clearMatchContext(playerId);
            exitMatchingState(stateLogic, playerId);
            pushMatchCancel(playerId);
            affected++;
        }
        if (affected > 0) {
            LogCore.core.info("PlayerService 完成 MatchService 断开匹配清理: service={}, affected={}",
                    owner().getId(), affected);
        }
    }

    private boolean isMatchingState(PlayerMapState state) {
        return state == PlayerMapState.MATCHING || state == PlayerMapState.TEAM_MATCHING;
    }

    private DBMatchContext getMatchContext(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        return data == null ? null : data.getMatchContext();
    }

    private boolean isActiveMatch(DBMatchContext context) {
        return context != null && context.getMatchStartMill() > 0L;
    }

    private void clearMatchContext(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data != null) {
            data.setMatchContext(new DBMatchContext());
        }
    }

    private void exitMatchingState(PlayerMapStateLogic stateLogic, long playerId) {
        PlayerMapState state = stateLogic.getState(playerId);
        if (isMatchingState(state)) {
            stateLogic.exit(playerId, state);
        }
    }

    private void pushMatchCancel(long playerId) {
        RpcResult<Void> result = ConnServiceRpcProxy.callPushToPlayerId(
                playerId, ClientFrameChunk.wrap(
                        MatchMsgId.S2C_MATCH_CANCEL_VALUE,
                        S2C_CancelMatch.newBuilder()
                                .setSuccess(true)
                                .setMessage("匹配服务断开，已取消匹配")
                                .build()));
        if (!result.isSuccess()) {
            LogCore.core.warn("PlayerService 通知客户端取消匹配失败: playerId={}, errorCode={}, message={}",
                    playerId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    private PlayerOfflineLogic offline() {
        return owner().getActor(PlayerOfflineLogic.class);
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
