package org.evd.game.common.serializeBean.LobbyService.role;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

@SerializeClass
public class SLobbyRoleSnapshot implements ISerializable {
    private long playerId;
    private int characterId;
    private String name;
    private int level;

    public SLobbyRoleSnapshot() {
    }

    public SLobbyRoleSnapshot(long playerId, int characterId, String name, int level) {
        this.playerId = playerId;
        this.characterId = characterId;
        this.name = name;
        this.level = level;
    }

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public int getCharacterId() { return characterId; }
    public void setCharacterId(int characterId) { this.characterId = characterId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
