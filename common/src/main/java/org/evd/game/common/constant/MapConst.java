package org.evd.game.common.constant;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.exception.SysException;

/** 地图相关常量和路由方法。 */
public final class MapConst {
    private MapConst() {
    }

    /** 地图配置对应的 Deal 类型。 */
    public enum MapType {
        NORMAL(1);

        private final int type;

        MapType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    /** 根据地图配置选择地图管理服务；跨服地图路由统一从这里扩展。 */
    public static CallPoint getSceneManagerCallPoint(int mapCfgId) {
        if (mapCfgId <= 0) {
            throw new SysException("地图配置 ID 非法: mapCfgId={}", mapCfgId);
        }
        return Service.getCurrent().getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
    }
}
