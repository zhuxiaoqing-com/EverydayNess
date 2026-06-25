package org.evd.game.runtime.config;

import java.util.ArrayList;
import java.util.List;

public class NodeConfig {
    private String dbConfigPath;
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

}
