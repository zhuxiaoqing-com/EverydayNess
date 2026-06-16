package org.evd.game.annotation;

public enum RpcActorType {
    NONE(null),
    PLAYER(ServiceType.StageService),
    MAP(ServiceType.StageService),
    GATE(ServiceType.ConnService),
    GUILD(ServiceType.StageService),
    MAP_PLAYER(ServiceType.StageService),
    ;

    private final ServiceType ownerServiceType;

    RpcActorType(ServiceType ownerServiceType) {
        this.ownerServiceType = ownerServiceType;
    }

    public ServiceType getOwnerServiceType() {
        return ownerServiceType;
    }
}
