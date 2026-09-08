package org.evd.game.common.serializeBean.SceneManagerService.routing;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.actor.ActorAddress;

/** 场景初始化使用的玩家完整基础数据。 */
@SerializeClass
public final class SPlayerMapData implements ISerializable {
    private long playerId;
    private String name;
    private int level;
    private ActorAddress gateActorAddress;
    private ActorAddress playerActorAddress;

    public SPlayerMapData() {
    }

    public SPlayerMapData(long playerId, String name, int level) {
        this.playerId = playerId;
        this.name = name;
        this.level = level;
    }

    public SPlayerMapData(SPlayerMapData other) {
        this(other == null ? 0L : other.playerId,
                other == null ? null : other.name,
                other == null ? 0 : other.level);
        if (other != null) {
            this.gateActorAddress = other.gateActorAddress == null
                    ? null : new ActorAddress(other.gateActorAddress);
            this.playerActorAddress = other.playerActorAddress == null
                    ? null : new ActorAddress(other.playerActorAddress);
        }
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public ActorAddress getGateActorAddress() {
        return gateActorAddress;
    }

    public void setGateActorAddress(ActorAddress gateActorAddress) {
        this.gateActorAddress = gateActorAddress == null ? null : new ActorAddress(gateActorAddress);
    }

    public ActorAddress getPlayerActorAddress() {
        return playerActorAddress;
    }

    public void setPlayerActorAddress(ActorAddress playerActorAddress) {
        this.playerActorAddress = playerActorAddress == null ? null : new ActorAddress(playerActorAddress);
    }
}
