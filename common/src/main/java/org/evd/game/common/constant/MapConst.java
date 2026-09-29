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
        /** 普通地图。 */
        NORMAL(1),
        /** 多人匹配地图。 */
        MULTI_MATCH(2),
        /** 阵营地图。 */
        CAMP(3);

        private final int type;

        MapType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    /** 地图进入处理结果。 */
    public enum SceneEnterResult {
        /** 场景接受了玩家进入请求。 */
        SUCCESS,
        /** 场景存在，但拒绝了本次进入请求。 */
        REJECTED,
        /** Stage 上不存在请求对应的场景或 sceneId 已过期。 */
        SCENE_NOT_FOUND
    }

    /** 玩家退出场景的触发原因。使用常量，避免业务状态依赖枚举序列化。 */
    public interface RoleExitType {
        /** 玩家正常退出场景。 */
        int NORMAL = 0;
        /** 玩家进入目标场景失败后退出。 */
        int ENTER_FAILED = 1;
        /** PlayerService 断开时清理场景玩家。 */
        int PLAYER_SERVICE_DISCONNECT = 2;
        /** 当前场景销毁时退出；没有其他转场时由 PlayerService 进入默认地图。 */
        int MAP_DESTROYED = 3;

        static String name(int type) {
            return switch (type) {
                case NORMAL -> "NORMAL";
                case ENTER_FAILED -> "ENTER_FAILED";
                case PLAYER_SERVICE_DISCONNECT -> "PLAYER_SERVICE_DISCONNECT";
                case MAP_DESTROYED -> "MAP_DESTROYED";
                default -> "UNKNOWN(" + type + ")";
            };
        }
    }

    /** 根据地图配置选择地图管理服务；跨服地图路由统一从这里扩展。 */
    public static CallPoint getSceneManagerCallPoint(int mapCfgId) {
        if (mapCfgId <= 0) {
            throw new SysException("地图配置 ID 非法: mapCfgId={}", mapCfgId);
        }
        return Service.getCurrent().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
    }
}
