package org.evd.game.annotation;

public enum RpcActorType {
    NONE(null),
    PLAYER("PlayerService"),
    MAP("StageService"),
    GATE("ConnService"),
    MAP_PLAYER("StageService"),
    ;

    private final String ownerServiceClassName;

    RpcActorType(String ownerServiceClassName) {
        this.ownerServiceClassName = ownerServiceClassName;
    }

    public String getOwnerServiceClassName() {
        return ownerServiceClassName;
    }
}
