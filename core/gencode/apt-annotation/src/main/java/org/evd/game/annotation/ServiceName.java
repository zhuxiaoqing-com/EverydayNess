package org.evd.game.annotation;

import java.util.Arrays;

public final class ServiceName {
    public static final String CONN_SERVICE = "ConnService";
    public static final String DB_SERVICE = "DBService";
    public static final String LOCATION_SERVICE = "LocationService";
    public static final String STAGE_SERVICE = "StageService";
    public static final String PLAYER_SERVICE = "PlayerService";

    private static final String[] VALUES = {
            CONN_SERVICE,
            DB_SERVICE,
            LOCATION_SERVICE,
            STAGE_SERVICE,
            PLAYER_SERVICE
    };

    private ServiceName() {
    }

    public static String[] values() {
        return Arrays.copyOf(VALUES, VALUES.length);
    }

    public static String fullClassName(String className) {
        return "org.evd.game." + className + "." + className;
    }
}
