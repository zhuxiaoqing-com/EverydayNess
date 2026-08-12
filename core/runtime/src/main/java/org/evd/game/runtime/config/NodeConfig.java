package org.evd.game.runtime.config;

import java.util.ArrayList;
import java.util.List;

public class NodeConfig {
    private String dbConfigPath;
    private boolean debug;
    private int platformId;
    private int serverId;
    private List<NodeInfo> nodes = new ArrayList<>();

    public String getDbConfigPath() {
        return dbConfigPath;
    }

    public void setDbConfigPath(String dbConfigPath) {
        this.dbConfigPath = dbConfigPath;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getPlatformId() {
        return platformId;
    }

    public void setPlatformId(int platformId) {
        this.platformId = platformId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }
}
