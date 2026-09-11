package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 玩家进入地图时的通用扩展参数。 */
@SerializeClass
public final class SPlayerEnterParam implements ISerializable {
    private MatchPlayerEnterMapParam matchParam;

    public SPlayerEnterParam() {
    }

    public SPlayerEnterParam(SPlayerEnterParam other) {
        this.matchParam = other == null || other.matchParam == null
                ? null : new MatchPlayerEnterMapParam(other.matchParam);
    }

    public SPlayerEnterParam(MatchPlayerEnterMapParam matchParam) {
        this.matchParam = matchParam == null ? null : new MatchPlayerEnterMapParam(matchParam);
    }

    public MatchPlayerEnterMapParam getMatchParam() {
        return matchParam;
    }

    public void setMatchParam(MatchPlayerEnterMapParam matchParam) {
        this.matchParam = matchParam == null ? null : new MatchPlayerEnterMapParam(matchParam);
    }
}
