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
    SDK(2, ServiceName.SDK_SERVICE),
    STAGE(3, ServiceName.STAGE_SERVICE),
    SCENE_MANAGER(4, ServiceName.SCENE_MANAGER_SERVICE),
    LOBBY(5, ServiceName.LOBBY_SERVICE),
    PLAYER(6, ServiceName.PLAYER_SERVICE),
    DB(7, ServiceName.DB_SERVICE),
    LOC(8, ServiceName.LOCATION_SERVICE),
    ADMIN(9, ServiceName.ADMIN_SERVICE),
    GLOC(10, ServiceName.LOCATION_SERVICE),
    GSTAGE(11, ServiceName.STAGE_SERVICE),
    GSCENE_MANAGER(12, ServiceName.SCENE_MANAGER_SERVICE),

    ;
    int type;
    String className;

    static ServiceType[] shutdownOrder = new ServiceType[] {
            CONN,
            SDK,
            SCENE_MANAGER,
            STAGE,
            LOBBY,
            PLAYER,
            DB,
            LOC,
    };

    public static ServiceType[] shutdownOrder() {
        return shutdownOrder;
    }
    public static int shutdownOrderId(ServiceType serviceType) {
        for (int i = 0; i < shutdownOrder.length; i++) {
            if(shutdownOrder[i] == serviceType) {
                return i;
            }
        }
        return 9999;
    }


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
