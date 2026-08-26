package org.evd.game.annotation.service;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class ServiceName {
    public static final String ADMIN_SERVICE = "AdminService";
    public static final String CONN_SERVICE = "ConnService";
    public static final String DB_SERVICE = "DBService";
    public static final String LOCATION_SERVICE = "LocationService";
    public static final String STAGE_SERVICE = "StageService";
    public static final String PLAYER_SERVICE = "PlayerService";
    public static final String SCENE_MANAGER_SERVICE = "SceneManagerService";
    public static final String LOBBY_SERVICE = "LobbyService";
    public static final String SDK_SERVICE = "SdkService";
    public static final String ONLINE_SERVICE = "OnlineService";

    private static final String[] VALUES = {
            ADMIN_SERVICE,
            CONN_SERVICE,
            DB_SERVICE,
            LOCATION_SERVICE,
            STAGE_SERVICE,
            PLAYER_SERVICE,
            SCENE_MANAGER_SERVICE,
            LOBBY_SERVICE,
            SDK_SERVICE,
            ONLINE_SERVICE
    };

    private ServiceName() {
    }

    public static String[] values() {
        return Arrays.copyOf(VALUES, VALUES.length);
    }

    public static String fullClassName(String className) {
        return "org.evd.game." + className + "." + className;
    }

    public static String fullProxyClassName(String className) {
        return "org.evd.game.common.proxy." + className + "." + className+"Proxy";
    }

    public static Object getRpcProxyObj(String className) {
        String proxyClassName = fullProxyClassName(className);
        try {
            Class<?> aClass = Class.forName(proxyClassName);

            Field field = aClass.getDeclaredField("INSTANCE");
            field.setAccessible(true);

            return field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

