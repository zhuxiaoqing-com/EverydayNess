package org.evd.game.runtime.actor;

public enum ActorType {
    PLAYER(1),
    MAP(2),
    GATE(3),
    GUILD(4),
    MAP_PLAYER(5);

    private final int code;

    ActorType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
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
