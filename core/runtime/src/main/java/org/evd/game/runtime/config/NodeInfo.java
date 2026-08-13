package org.evd.game.runtime.config;

import io.netty.util.internal.StringUtil;
import org.evd.game.annotation.NodeType;
import org.evd.game.runtime.netty.AddressInfo;

import java.util.List;

public class NodeInfo {
    private int nodeId = -1;
    private String name;
    private String addr;
    private NodeType nodeType;
    /** 链路优先级：低层级主动连接高层级；同层级再按 nodeId 比较。 */
    private int linkLevel;
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

    public int getLinkLevel() {
        return linkLevel;
    }

    public void setLinkLevel(int linkLevel) {
        this.linkLevel = linkLevel;
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
        if (localNode.getLinkLevel() != remoteNode.getLinkLevel()) {
            return localNode.getLinkLevel() < remoteNode.getLinkLevel();
        }
        return localNode.getNodeId() > remoteNode.getNodeId();
    }
}
