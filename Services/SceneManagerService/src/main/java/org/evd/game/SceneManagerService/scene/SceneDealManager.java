package org.evd.game.SceneManagerService.scene;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.SceneManagerService.scene.deal.NormalSceneDeal;
import org.evd.game.SceneManagerService.scene.deal.CampSceneDeal;
import org.evd.game.common.config.table.MapConfig;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.exception.SysException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SceneManager 的 Deal 注册表和分发入口。 */
@Slf4j
public final class SceneDealManager {
    private final Map<Integer, AbstractSceneDeal> deals = new HashMap<>();

    public SceneDealManager(SceneManagerService owner) {
        deals.put(MapConst.MapType.NORMAL.getType(), new NormalSceneDeal(owner));
        deals.put(MapConst.MapType.MULTI_MATCH.getType(), new CampSceneDeal(owner));
        deals.put(MapConst.MapType.CAMP.getType(), new CampSceneDeal(owner));
    }

    public AbstractSceneDeal getDeal(int mapCfgId) {
        MapConfig mapConfig = MapConfigs.get(mapCfgId);
        if (mapConfig == null) {
            throw new SysException("地图配置不存在: mapCfgId={}", mapCfgId);
        }
        int mapType = mapConfig.getType();
        AbstractSceneDeal deal = deals.get(mapType);
        if (deal == null) {
            throw new SysException("没有对应的地图 Deal: mapCfgId={}, mapType={}", mapCfgId, mapType);
        }
        return deal;
    }

    public SMapInfo createScene(SMapCreateRequest request) {
        if (request == null || request.getMapKey() == null) {
            throw new SysException("地图创建请求缺少地图标识");
        }
        MapConfig mapConfig = MapConfigs.get(request.getMapKey().getMapCfgId());
        if (mapConfig == null) {
            throw new SysException("地图配置不存在: mapCfgId={}", request.getMapKey().getMapCfgId());
        }
        return getDeal(request.getMapKey().getMapCfgId()).createScene(request);
    }

    public CallPoint getSceneStage(long sceneId) {
        for (AbstractSceneDeal deal : deals.values()) {
            CallPoint stage = deal.findSceneStage(sceneId);
            if (stage != null) {
                return stage;
            }
        }
        return null;
    }

    public void onStageServiceDisconnect(CallPoint stage) {
        for (AbstractSceneDeal deal : deals.values()) {
            deal.onStageServiceDisconnect(stage);
        }
    }

    /**
     * 用 Stage 当前快照全量替换该 Stage 的场景路由。
     *
     * <p>这里不能直接逐条恢复快照：如果 Stage 在断链期间销毁了场景，
     * SceneManager 里的旧路由也必须一并清掉，否则旧场景仍可能被当成有效路由。</p>
     *
     * <p>所以处理顺序固定为：先保存并清理旧路由，再按快照恢复，最后分别打印
     * 已不存在、仍存在和新增的场景。</p>
     */
    public int replaceStageScenes(CallPoint stage, List<SMapInfo> maps) {
        // 先清理该 Stage 的旧路由，但把旧数据按 mapKey 保留下来，后面还要和新快照做对比。
        Map<SMapKey, SMSceneInfo> oldScenes = new HashMap<>();
        for (AbstractSceneDeal deal : deals.values()) {
            oldScenes.putAll(deal.clearStageScenes(stage));
        }

        // 新快照也按 mapKey 建索引，方便判断同一个地图是否还在。
        Map<SMapKey, SMapInfo> currentScenes = new HashMap<>();
        for (SMapInfo map : maps) {
            currentScenes.put(map.toMapKey(), map);
        }
        List<SMSceneInfo> disappearedScenes = new ArrayList<>();
        List<SMSceneInfo> existingScenes = new ArrayList<>();
        List<SMapInfo> addedScenes = new ArrayList<>();

        // 旧场景逐个和新快照比对：匹配 mapKey 且 sceneId 不变，说明场景仍然存在；否则就是已经消失。
        for (SMSceneInfo oldScene : oldScenes.values()) {
            SMapInfo currentScene = currentScenes.get(oldScene.getMapKey());
            if (currentScene != null && currentScene.getSceneId() == oldScene.getSceneId()) {
                existingScenes.add(oldScene);
            } else {
                disappearedScenes.add(oldScene);
            }
        }

        // 新快照逐个和旧场景比对：找不到相同 mapKey + sceneId 的，就是 Stage 新增的场景。
        for (SMapInfo map : maps) {
            SMSceneInfo oldScene = oldScenes.get(map.toMapKey());
            if (oldScene == null || oldScene.getSceneId() != map.getSceneId()) {
                addedScenes.add(map);
            }
        }

        // 完成分类后，再把 Stage 当前快照重新写回 SceneManager。
        for (SMapInfo map : maps) {
            restoreScene(stage, map);
        }

        // 恢复完成后分三组打印，便于核对 Stage 断链期间场景的变化。
        log.info("SceneManager 恢复后已不存在的 Stage 场景: stage={}, count={}",
                stage, disappearedScenes.size());
        for (SMSceneInfo scene : disappearedScenes) {
            log.info("SceneManager Stage 场景已不存在: stage={}, mapKey={}, sceneId={}, oldState={}",
                    stage, scene.getMapKey(), scene.getSceneId(), scene.getState());
        }
        log.info("SceneManager 恢复后仍存在的 Stage 场景: stage={}, count={}",
                stage, existingScenes.size());
        for (SMSceneInfo scene : existingScenes) {
            log.info("SceneManager Stage 场景仍存在: stage={}, mapKey={}, sceneId={}",
                    stage, scene.getMapKey(), scene.getSceneId());
        }
        log.info("SceneManager 恢复后新增的 Stage 场景: stage={}, count={}",
                stage, addedScenes.size());
        for (SMapInfo scene : addedScenes) {
            log.info("SceneManager Stage 新增场景: stage={}, mapKey={}, sceneId={}",
                    stage, scene.toMapKey(), scene.getSceneId());
        }
        return disappearedScenes.size();
    }

    public void restoreScene(CallPoint stage, SMapInfo map) {
        getDeal(map.getMapCfgId()).restoreScene(stage, map);
    }

}
