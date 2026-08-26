package org.evd.game.runtime.ymlconfig;

import io.netty.util.internal.StringUtil;
import org.evd.game.annotation.node.NodeType;
import org.evd.game.runtime.netty.AddressInfo;

import java.util.List;

public class NodeInfo {
    private int nodeId = -1;
    private String name;
    private String addr;
    private NodeType nodeType;
    private AddressInfo addressInfo;
    private List<ScheduleInfo> schedule;

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public List<ScheduleInfo> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<ScheduleInfo> schedule) {
        this.schedule = schedule;
    }


    public AddressInfo getAddressInfo() {
        if (addressInfo == null && !StringUtil.isNullOrEmpty(addr)) {
            addressInfo = new AddressInfo(addr);
        }
        return addressInfo;
    }

    public static boolean needConnect(NodeInfo localNode, NodeInfo remoteNode) {
        if (localNode.getNodeType() != NodeType.GAME) {
            return false;
        }

        if (remoteNode.getNodeType() != NodeType.GAME) {
            return true;
        }

        return localNode.getNodeId() > remoteNode.getNodeId();
    }
}
