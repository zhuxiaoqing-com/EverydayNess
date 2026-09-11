package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.common.serializeBean.MatchService.match.SMatchSceneCreateParams;

/** 通用地图创建请求；具体地图由 mapType 选择对应的场景实现。 */
@SerializeClass
public final class SMapCreateRequest implements ISerializable {
    private SMapKey mapKey;
    private SMatchSceneCreateParams matchParams;

    public SMapCreateRequest() {
    }

    public SMapCreateRequest(SMapKey mapKey, SMatchSceneCreateParams matchParams) {
        this.mapKey = mapKey == null ? null : new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
        this.matchParams = matchParams == null ? null : new SMatchSceneCreateParams(matchParams);
    }

    public SMapCreateRequest(SMapCreateRequest other) {
        this(other == null ? null : other.mapKey,
                other == null ? null : other.matchParams);
    }

    public SMapKey getMapKey() {
        return mapKey;
    }

    public void setMapKey(SMapKey mapKey) {
        this.mapKey = mapKey == null ? null : new SMapKey(mapKey.getMapCfgId(), mapKey.getGroupId());
    }

    public SMatchSceneCreateParams getMatchParams() {
        return matchParams;
    }

    public void setMatchParams(SMatchSceneCreateParams matchParams) {
        this.matchParams = matchParams == null ? null : new SMatchSceneCreateParams(matchParams);
    }
}
