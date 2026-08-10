package org.evd.game.annotation;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public enum ServiceType {
    CONN(1, ServiceName.CONN_SERVICE, false),
    SDK(2, ServiceName.SDK_SERVICE, false),
    STAGE(3, ServiceName.STAGE_SERVICE, false),
    SCENE_MANAGER(4, ServiceName.SCENE_MANAGER_SERVICE),
    LOBBY(5, ServiceName.LOBBY_SERVICE),
    PLAYER(6, ServiceName.PLAYER_SERVICE, false),
    DB(7, ServiceName.DB_SERVICE),
    LOC(8, ServiceName.LOCATION_SERVICE),
    ADMIN(9, ServiceName.ADMIN_SERVICE),
    GLOC(100, ServiceName.LOCATION_SERVICE),
    GSTAGE(101, ServiceName.STAGE_SERVICE, false),
    GSCENE_MANAGER(102, ServiceName.SCENE_MANAGER_SERVICE),

    ;
    final int type;
    final String className;
    final boolean single;

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
        // 保证admin 最后一个停
        if(serviceType == ADMIN) {
            return 9999;
        }
        return 999;
    }

    public static boolean isGlobal(ServiceType serviceType) {
        return serviceType.getType() >= 100;
    }


    ServiceType(int type, String name) {
        this(type, name, true);
    }

    ServiceType(int type, String name, boolean single) {
        this.type = type;
        this.className = name;
        this.single = single;
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

    public boolean isSingle() {
        return single;
    }

    public boolean isGlobal() {
        return ServiceType.isGlobal(this);
    }

    public boolean isSupportFlush() {
        return this == PLAYER;
    }

    @Override
    public String toString() {
        return "ServiceType{" +
                "type=" + type +
                ", name='" + className + '\'' +
                ", single=" + single +
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
