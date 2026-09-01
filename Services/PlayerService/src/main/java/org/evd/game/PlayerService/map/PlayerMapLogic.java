package org.evd.game.PlayerService.map;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MapRoute;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;

/** PlayerService 的玩家进入、完成和离开地图逻辑。 */
@Actor
public final class PlayerMapLogic {
    private static final int DEFAULT_MAP_CONFIG_ID = 1;

    /** 执行玩家进入地图流程；进入失败通过异常交给上线流程处理。 */
    public void enterMap(long playerId) {
        PlayerService owner = owner();
        PlayerSessionManager sessionManager = owner.sessionManager();
        if (!sessionManager.beginEnterMap(playerId)) {
            throw new SysException("玩家当前状态不允许进入地图: playerId={}", playerId);
        }
        LogCore.core.info("PlayerService 发起进入地图: playerId={}", playerId);

        RpcResult<MapRoute> acquireResult = SceneManagerRpcProxy.callAcquireMap(null, DEFAULT_MAP_CONFIG_ID);
        if (!acquireResult.isSuccess() || acquireResult.getValue() == null) {
            throw new SysException("获取地图实例失败: playerId={}, errorCode={}, message={}",
                    playerId, acquireResult.getErrorCode(), acquireResult.getErrorMessage());
        }
        MapRoute route = acquireResult.getValue();
        RpcResult<Boolean> enterResult = StageServiceRpcProxy.callEnterMap(
                route.getStage(), route.getMapInstanceId(), playerId,
                sessionManager.getMapEnterSeq(playerId));
        if (!enterResult.isSuccess() || !Boolean.TRUE.equals(enterResult.getValue())) {
            throw new SysException("Stage 进入地图失败: playerId={}, mapInstanceId={}, errorCode={}, message={}",
                    playerId, route.getMapInstanceId(), enterResult.getErrorCode(), enterResult.getErrorMessage());
        }
        if (!sessionManager.completeEnterMap(playerId, route)) {
            throw new SysException("玩家不在进入地图状态: playerId={}", playerId);
        }
        LogCore.core.info("PlayerService 进入地图完成: playerId={}, mapInstanceId={}",
                playerId, route.getMapInstanceId());
    }

    /** 玩家离线时从当前 Stage 移除；地图不存在时按幂等成功处理。 */
    public void leaveMap(long playerId, MapRoute route, long enterSeq) {
        if (route == null || route.getStage() == null || route.getMapInstanceId() <= 0L) {
            return;
        }
        RpcResult<Boolean> result = StageServiceRpcProxy.callLeaveMap(
                route.getStage(), route.getMapInstanceId(), playerId, enterSeq);
        boolean success = result.isSuccess() && Boolean.TRUE.equals(result.getValue());
        if (!success) {
            LogCore.core.warn("PlayerService 离开地图失败: playerId={}, mapInstanceId={}, errorCode={}, message={}",
                    playerId, route.getMapInstanceId(), result.getErrorCode(), result.getErrorMessage());
        }
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
