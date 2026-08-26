package org.evd.game.annotation.service;

import org.evd.game.annotation.node.NodeType;

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

    // 新枚举必须追加在已有枚举之后，避免改变已有 ServiceType 的序列化 ordinal。
    ZLOC(50, ServiceName.LOCATION_SERVICE,true, NodeType.ZONE),
    ZSTAGE(51, ServiceName.STAGE_SERVICE, false, NodeType.ZONE),
    ZSCENE_MANAGER(52, ServiceName.SCENE_MANAGER_SERVICE, true, NodeType.ZONE),

    GLOC(100, ServiceName.LOCATION_SERVICE,true, NodeType.GLOBAL),
    GSTAGE(101, ServiceName.STAGE_SERVICE, false, NodeType.GLOBAL),
    GSCENE_MANAGER(102, ServiceName.SCENE_MANAGER_SERVICE, true, NodeType.GLOBAL),

    ONLINE(10, ServiceName.ONLINE_SERVICE),

    ;
    final int type;
    final String className;
    final boolean single;
    final NodeType nodeType;

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

    /**
     * 自己管理actorAddress 不走自动重试查找最新的这一套;
     * 目前只有CONN, PLAYER, STAGE 这三种需要location定位;
     * actorAddress 过期也由对应的Service自己管理，MessageLocationSender不负责清理;
     */
    public boolean selfManageActorAddress() {
        return switch (this) {
            case STAGE, ZSTAGE, GSTAGE, PLAYER, ONLINE, CONN -> true;
            default -> false;
        };
    }

    public static boolean isGame(ServiceType serviceType) {
        return serviceType.getNodeType() == NodeType.GAME;
    }


    ServiceType(int type, String name) {
        this(type, name, true, NodeType.GAME);
    }

    ServiceType(int type, String name, boolean single) {
        this(type, name, single, NodeType.GAME);
    }

    ServiceType(int type, String name, boolean single, NodeType nodeType) {
        this.type = type;
        this.className = name;
        this.single = single;
        this.nodeType = nodeType;
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

    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean isSupportNodeType(NodeType nodeType) {
        return this.nodeType == nodeType;
    }

    public boolean isGame() {
        return ServiceType.isGame(this);
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

