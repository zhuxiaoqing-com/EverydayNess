package org.evd.game.runtime.ymlconfig;


import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.exception.SysException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GlobalYml {
    private static NodeYml nodeConfig;
    private static DbYml dbConfig;
    private static NodeInfo localNodeInfo;

    private GlobalYml() {
    }

    public static synchronized void init(String bootStrapName, int localNodeId) {
        if (nodeConfig != null && dbConfig != null && localNodeInfo != null) {
            return;
        }

        String configPath = ConstPath.CONFIGURATION_PATH + bootStrapName;
        nodeConfig = loadYaml(configPath, NodeYml.class);
        if (nodeConfig == null) {
            throw new SysException("bootstrap config is empty: {}", configPath);
        }
        if (nodeConfig.getDbConfigPath() == null || nodeConfig.getDbConfigPath().isBlank()) {
            throw new SysException("bootstrap dbConfig is empty: {}", configPath);
        }
        String dbPath = ConstPath.CONFIGURATION_PATH + nodeConfig.getDbConfigPath();
        dbConfig = loadYaml(dbPath, DbYml.class);
        if (dbConfig == null) {
            throw new SysException("db config is empty: {}", dbPath);
        }
        nodeConfig.validate(dbConfig);
        localNodeInfo = requireNodeInfo(localNodeId);
    }

    public static NodeYml requireNodeConfig() {
        if (nodeConfig == null) {
            throw new SysException("GlobalYml nodeConfig not initialized");
        }
        return nodeConfig;
    }

    public static DbYml requireDbConfig() {
        if (dbConfig == null) {
            throw new SysException("GlobalYml dbConfig not initialized");
        }
        return dbConfig;
    }

    public static NodeInfo requireNodeInfo(int nodeId) {
        for (NodeInfo nodeInfo : requireNodeConfig().getNodes()) {
            if (nodeInfo.getNodeId() == nodeId) {
                return nodeInfo;
            }
        }
        throw new SysException("node config not exist: {}", nodeId);
    }

    public static NodeInfo requireLocalNodeInfo() {
        if (localNodeInfo == null) {
            throw new SysException("GlobalYml localNodeInfo not initialized");
        }
        return localNodeInfo;
    }

    public static Path requireTableDir() {
        String directory = requireNodeConfig().getTableDir();
        if (directory == null || directory.isBlank()) {
            throw new SysException("bootstrap tableDir is empty");
        }
        return Path.of(directory).normalize();
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

    /**
     * 从1开始
     */
    public static String getServiceName(ServiceInfo serviceInfo, int index) {
        return serviceInfo.getInstanceName(index);
    }

    public static List<RegisteredService> getAllRegisteredServiceList() {
        List<RegisteredService> returnList = new ArrayList<>();

        for (NodeInfo nodeInfo : requireNodeConfig().getNodes()) {
            for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    int serviceNum = Math.max(1, serviceInfo.getNum());
                    String className = serviceInfo.getClassName();
                    String serviceClassName = "org.evd.game." + className + "." + className;
                    for (int index = 1; index <= serviceNum; index++) {
                        returnList.add(new RegisteredService(
                                serviceInfo.getServiceType(),
                                serviceClassName,
                                getServiceName(serviceInfo, index),
                                requireNodeConfig().getPlatformId(),
                                requireNodeConfig().getServerId(),
                                nodeInfo.getNodeId()));
                    }
                }
            }
        }

        // 按关服顺序排序
        return returnList.stream()
                .sorted(Comparator.comparingInt(a -> ServiceType.shutdownOrderId(a.getServiceType())))
                .toList();
    }
}
