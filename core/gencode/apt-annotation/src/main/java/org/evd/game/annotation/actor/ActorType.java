package org.evd.game.annotation.actor;

import org.evd.game.annotation.service.ServiceName;

public enum ActorType {
    NONE(0, null),
    PLAYER(1, ServiceName.PLAYER_SERVICE),
    MAP(2, ServiceName.STAGE_SERVICE),
    GATE(3, ServiceName.CONN_SERVICE),
    MAP_PLAYER(4, ServiceName.STAGE_SERVICE);

    private final int code;
    private final String ownerServiceClassName;

    ActorType(int code, String ownerServiceClassName) {
        this.code = code;
        this.ownerServiceClassName = ownerServiceClassName;
    }

    public int getCode() {
        return code;
    }

    public String getOwnerServiceClassName() {
        return ownerServiceClassName;
    }

    public String getOwnerServiceClass() {
        return ownerServiceClassName;
    }

    public static ActorType fromCode(int code) {
        for (ActorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 ActorType code: " + code);
    }
}

