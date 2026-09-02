package org.evd.game.StageService;

import org.evd.game.StageService.scene.SceneBattle;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.proxy.StageService.StageServiceRpcProxy;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

import java.util.HashMap;
import java.util.Map;

/** StageService 的场景生命周期和玩家进入逻辑。 */
public class StageService extends Service {
    private final Map<Long, SceneBattle> scenes = new HashMap<>();
    private final Map<SMapKey, Long> sceneIds = new HashMap<>();

    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    /** 返回当前 Stage 已创建的地图数量，供 SceneManager 做负载均衡。 */
    public int getMapCount() {
        return scenes.size();
    }

    /** 创建 SceneBattle；相同 sceneId 重复创建时保持幂等。 */
    public boolean createScene(SMapKey mapKey, long sceneId) {
        if (mapKey == null || mapKey.getMapCfgId() <= 0 || sceneId <= 0L) {
            return false;
        }
        SceneBattle current = scenes.get(sceneId);
        if (current != null) {
            return current.getMapKey().equals(mapKey);
        }
        Long oldSceneId = sceneIds.putIfAbsent(mapKey, sceneId);
        if (oldSceneId != null) {
            return oldSceneId == sceneId;
        }
        registerActor(ActorId.map(sceneId), MailBoxType.ORDERED);
        scenes.put(sceneId, new SceneBattle(mapKey, sceneId));
        return true;
    }

    /** 进入目标 SceneBattle 的预加入队列，然后退出旧场景。 */
    public void prepareEnterScene(SMapEnterRequest request) {
        if (request == null || request.getTargetInfo() == null) {
            LogCore.core.warn("StageService 收到空的预进入请求");
            return;
        }
        SMapInfo targetInfo = request.getTargetInfo();
        Long sceneId = sceneIds.get(targetInfo.toMapKey());
        SceneBattle scene = sceneId == null ? null : scenes.get(sceneId);
        if (scene == null) {
            LogCore.core.warn("StageService 根据 SMapKey 找不到目标 SceneBattle: playerId={}, sceneId={}, mapCfgId={}, groupId={}",
                    request.getPlayerId(), targetInfo.getSceneId(), targetInfo.getMapCfgId(), targetInfo.getGroupId());
            return;
        }
        if (!scene.canEnter(request)) {
            LogCore.core.warn("StageService SceneBattle 拒绝玩家预进入: playerId={}, sceneId={}, transferId={}",
                    request.getPlayerId(), targetInfo.getSceneId(), request.getTransferId());
            return;
        }
        scene.addPendingRole(request);

        SMapInfo oldMapInfo = request.getOldMapInfo();
        if (oldMapInfo != null && oldMapInfo.getSceneId() > 0L
                && oldMapInfo.getSceneId() != targetInfo.getSceneId()) {
            RpcResult<CallPoint> oldStage = SceneManagerRpcProxy.callGetSceneStage(null, oldMapInfo.getSceneId());
            if (!oldStage.isSuccess() || oldStage.getValue() == null) {
                scene.cancelPending(request.getPlayerId());
                LogCore.core.error("StageService 找不到旧场景 Stage: playerId={}, oldSceneId={}, errorCode={}, message={}",
                        request.getPlayerId(), oldMapInfo.getSceneId(), oldStage.getErrorCode(), oldStage.getErrorMessage());
                return;
            }
            RpcResult<Boolean> exitResult = StageServiceRpcProxy.callExitScene(oldStage.getValue(),
                    oldMapInfo.getSceneId(), request.getPlayerId());
            if (!exitResult.isSuccess() || !Boolean.TRUE.equals(exitResult.getValue())) {
                scene.cancelPending(request.getPlayerId());
                LogCore.core.error("StageService 退出旧场景失败: playerId={}, oldSceneId={}, errorCode={}, message={}",
                        request.getPlayerId(), oldMapInfo.getSceneId(), exitResult.getErrorCode(), exitResult.getErrorMessage());
                return;
            }
        }

        RpcResult<Void> readyResult = PlayerMapRpcProxy.sendReadyEnterMap(request.getPlayerService(),
                request.getPlayerId(), request.getTransferId(), targetInfo);
        if (!readyResult.isSuccess()) {
            scene.cancelPending(request.getPlayerId());
            LogCore.core.error("StageService 通知 PlayerService 客户端加载地图失败: playerId={}, sceneId={}, errorCode={}, message={}",
                    request.getPlayerId(), targetInfo.getSceneId(), readyResult.getErrorCode(), readyResult.getErrorMessage());
        }
    }

    /** 客户端完成加载后，正式进入 SceneBattle。 */
    public boolean enterScene(long sceneId, long playerId, long transferId) {
        SceneBattle scene = scenes.get(sceneId);
        return scene != null && scene.enter(playerId, transferId);
    }

    /** 退出一个场景中的正式玩家或预加入玩家。 */
    public boolean exitScene(long sceneId, long playerId) {
        SceneBattle scene = scenes.get(sceneId);
        return scene == null || scene.exit(playerId);
    }

    /** 销毁空场景。 */
    public boolean destroyScene(long sceneId) {
        SceneBattle scene = scenes.get(sceneId);
        if (scene == null) {
            return true;
        }
        if (!scene.isEmpty()) {
            return false;
        }
        scenes.remove(sceneId);
        sceneIds.remove(scene.getMapKey());
        unregisterActor(ActorId.map(sceneId));
        return true;
    }
}
