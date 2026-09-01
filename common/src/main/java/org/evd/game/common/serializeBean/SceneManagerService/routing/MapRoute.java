package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

/** SceneManagerService 返回给玩家服务的地图实例路由。 */
@SerializeClass
public class MapRoute implements ISerializable {
    private int mapConfigId;
    private long mapInstanceId;
    private CallPoint stage;

    public MapRoute() {
    }

    public MapRoute(int mapConfigId, long mapInstanceId, CallPoint stage) {
        this.mapConfigId = mapConfigId;
        this.mapInstanceId = mapInstanceId;
        this.stage = stage == null ? null : new CallPoint(stage);
    }

    public int getMapConfigId() {
        return mapConfigId;
    }

    public void setMapConfigId(int mapConfigId) {
        this.mapConfigId = mapConfigId;
    }

    public long getMapInstanceId() {
        return mapInstanceId;
    }

    public void setMapInstanceId(long mapInstanceId) {
        this.mapInstanceId = mapInstanceId;
    }

    public CallPoint getStage() {
        return stage == null ? null : new CallPoint(stage);
    }

    public void setStage(CallPoint stage) {
        this.stage = stage == null ? null : new CallPoint(stage);
    }
}
