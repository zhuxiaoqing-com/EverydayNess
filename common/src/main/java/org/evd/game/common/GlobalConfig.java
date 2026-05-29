package org.evd.game.common;

import org.evd.game.runtime.config.InfraConfig;
import org.evd.game.runtime.config.NodeConfig;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.support.SysException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class GlobalConfig {
    private static NodeConfig nodeConfig;
    private static InfraConfig infraConfig;

    private GlobalConfig() {
    }

    public static synchronized void init(String bootStrapName) {
        if (nodeConfig != null && infraConfig != null) {
            return;
        }

        String configPath = ConstPath.CONFIGURATION_PATH + bootStrapName;
        nodeConfig = loadYaml(configPath, NodeConfig.class);
        if (nodeConfig == null) {
            throw new SysException("bootstrap config is empty: {}", configPath);
        }
        if (nodeConfig.getInfraConfig() == null || nodeConfig.getInfraConfig().isBlank()) {
            throw new SysException("bootstrap infraConfig is empty: {}", configPath);
        }

        String infraPath = ConstPath.CONFIGURATION_PATH + nodeConfig.getInfraConfig();
        infraConfig = loadYaml(infraPath, InfraConfig.class);
        if (infraConfig == null) {
            throw new SysException("infra config is empty: {}", infraPath);
        }
    }

    public static NodeConfig requireNodeConfig() {
        if (nodeConfig == null) {
            throw new SysException("GlobalConfig nodeConfig not initialized");
        }
        return nodeConfig;
    }

    public static InfraConfig requireInfraConfig() {
        if (infraConfig == null) {
            throw new SysException("GlobalConfig infraConfig not initialized");
        }
        return infraConfig;
    }

    public static NodeInfo requireNodeInfo(String nodeId) {
        for (NodeInfo nodeInfo : requireNodeConfig().getNodes()) {
            if (nodeInfo.getName().equals(nodeId)) {
                return nodeInfo;
            }
        }
        throw new SysException("node config not exist: {}", nodeId);
    }

    private static <T> T loadYaml(String path, Class<T> type) {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(path)) {
            return yaml.loadAs(in, type);
        } catch (IOException e) {
            throw new SysException(e);
        }
    }
}
