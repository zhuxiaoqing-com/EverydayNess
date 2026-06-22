package org.evd.game.runtime.actor;

public enum ActorType {
    NONE(0, null),
    PLAYER(1, "PlayerService"),
    MAP(2, "StageService"),
    GATE(3, "ConnService"),
    GUILD(4, null),
    MAP_PLAYER(5, "StageService");

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

    public static ActorType fromCode(int code) {
        for (ActorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 ActorType code: " + code);
    }
}
