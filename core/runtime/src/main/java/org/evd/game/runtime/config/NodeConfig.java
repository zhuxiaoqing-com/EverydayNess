package org.evd.game.runtime.config;

import java.util.ArrayList;
import java.util.List;

public class NodeConfig {
    private String infraConfig;
    private List<NodeInfo> nodes = new ArrayList<>();

    public String getInfraConfig() {
        return infraConfig;
    }

    public void setInfraConfig(String infraConfig) {
        this.infraConfig = infraConfig;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

}
