package org.evd.game.runtime.config;

import java.util.ArrayList;
import java.util.List;

public class NodeConfig {
    private List<NodeInfo> nodes = new ArrayList<>();

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

}
