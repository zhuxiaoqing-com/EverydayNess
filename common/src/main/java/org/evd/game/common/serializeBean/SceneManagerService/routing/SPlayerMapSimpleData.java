package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** 地图分发和 Stage 判断使用的玩家简略数据。 */
@SerializeClass
public final class SPlayerMapSimpleData implements ISerializable {
    private long playerId;

    public SPlayerMapSimpleData() {
    }

    public SPlayerMapSimpleData(long playerId) {
        this.playerId = playerId;
    }

    public SPlayerMapSimpleData(SPlayerMapSimpleData other) {
        this(other == null ? 0L : other.playerId);
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }
}
