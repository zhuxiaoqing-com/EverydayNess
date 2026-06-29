package org.evd.game.annotation;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public enum ServiceType {
    CONN(1, ServiceName.CONN_SERVICE),
    DB(2, ServiceName.DB_SERVICE),
    LOC(3, ServiceName.LOCATION_SERVICE),
    STAGE(4, ServiceName.STAGE_SERVICE),
    PLAYER(5, ServiceName.PLAYER_SERVICE),
    GSTAGE(6, ServiceName.STAGE_SERVICE),
    SCENE_MANAGER(7, ServiceName.SCENE_MANAGER_SERVICE),
    GSCENE_MANAGER(8, ServiceName.SCENE_MANAGER_SERVICE),
    LOBBY(9, ServiceName.LOBBY_SERVICE),
    ONLINE(10, ServiceName.ONLINE_SERVICE),
    SDK(11, ServiceName.SDK_SERVICE),

    ;
    int type;
    String className;


    ServiceType(int type, String name) {
        this.type = type;
        this.className = name;
    }

    public int getType() {
        return type;
    }

    public String getClassName() {
        return className;
    }

    public String getServiceClassName() {
        return className;
    }

    @Override
    public String toString() {
        return "ServiceType{" +
                "type=" + type +
                ", name='" + className + '\'' +
                '}';
    }

    static ServiceType[] typeArray;
    static {
        typeArray = values();
        Map<Integer, List<ServiceType>> collect = Arrays.stream(typeArray)
                .collect(Collectors.groupingBy(ServiceType::getType));
        for (List<ServiceType> value : collect.values()) {
            if (value.size() > 1) {
                log.error("ServiceType 有重复的type {}", value);
                throw new RuntimeException("ServiceType 有重复的type " + value);
            }
        }
    }

    public static ServiceType byType(int type) {
        for (ServiceType serviceType : typeArray) {
            if(serviceType.type ==  type) {
                return serviceType;
            }
        }
        return null;
    }

    public static ServiceType byName(String className) {
        for (ServiceType serviceType : typeArray) {
            if(Objects.equals(serviceType.className, className)) {
                return serviceType;
            }
        }
        return null;
    }
}
