package org.evd.game.common;

import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.*;
import org.evd.game.runtime.support.SysException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class GlobalConfig {
    private static NodeConfig nodeConfig;
    private static DbConfig dbConfig;

    private GlobalConfig() {
    }

    public static synchronized void init(String bootStrapName) {
        if (nodeConfig != null && dbConfig != null) {
            return;
        }

        String configPath = ConstPath.CONFIGURATION_PATH + bootStrapName;
        nodeConfig = loadYaml(configPath, NodeConfig.class);
        if (nodeConfig == null) {
            throw new SysException("bootstrap config is empty: {}", configPath);
        }
        if (nodeConfig.getDbConfigPath() == null || nodeConfig.getDbConfigPath().isBlank()) {
            throw new SysException("bootstrap dbConfig is empty: {}", configPath);
        }

        String dbPath = ConstPath.CONFIGURATION_PATH + nodeConfig.getDbConfigPath();
        dbConfig = loadYaml(dbPath, DbConfig.class);
        if (dbConfig == null) {
            throw new SysException("db config is empty: {}", dbPath);
        }
    }

    public static NodeConfig requireNodeConfig() {
        if (nodeConfig == null) {
            throw new SysException("GlobalConfig nodeConfig not initialized");
        }
        return nodeConfig;
    }

    public static DbConfig requireDbConfig() {
        if (dbConfig == null) {
            throw new SysException("GlobalConfig dbConfig not initialized");
        }
        return dbConfig;
    }

    public static NodeInfo requireNodeInfo(String nodeId) {
        for (NodeInfo nodeInfo : requireNodeConfig().getNodes()) {
            if (nodeInfo.getName().equals(nodeId)) {
                return nodeInfo;
            }
        }
        throw new SysException("node config not exist: {}", nodeId);
    }

  /*  public static List<CallPoint> getCallLocalPoint(ServiceType serviceType) {
        for (NodeInfo node : nodeConfig.getNodes()) {
            for (ScheduleInfo scheduleInfo : node.getSchedule()) {
                for (ServiceInfo service : scheduleInfo.getServices()) {
                    serviceType
                }
            }
        }
    }*/



    private static <T> T loadYaml(String path, Class<T> type) {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(path)) {
            return yaml.loadAs(in, type);
        } catch (IOException e) {
            throw new SysException(e);
        }
    }
}
