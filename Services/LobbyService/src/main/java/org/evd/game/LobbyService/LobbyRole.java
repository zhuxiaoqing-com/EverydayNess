package org.evd.game.LobbyService;

import org.evd.game.common.proto.RoleData;

public final class LobbyRole {
    private final long playerId;
    private final String name;
    private final int level;

    public LobbyRole(long playerId, String name, int level) {
        this.playerId = playerId;
        this.name = name;
        this.level = level;
    }

    public long getPlayerId() {
        return playerId;
    }

    public RoleData toProto() {
        return RoleData.newBuilder()
                .setPlayerId(playerId)
                .setName(name)
                .setLevel(level)
                .build();
    }
}
