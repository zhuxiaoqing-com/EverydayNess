package org.evd.game.SceneManagerService.scene;

import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.SceneManagerService.scene.deal.NormalSceneDeal;
import org.evd.game.SceneManagerService.scene.deal.CampSceneDeal;
import org.evd.game.common.config.table.MapConfig;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.exception.SysException;

import java.util.HashMap;
import java.util.Map;

/** SceneManager 的 Deal 注册表和分发入口。 */
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

}
