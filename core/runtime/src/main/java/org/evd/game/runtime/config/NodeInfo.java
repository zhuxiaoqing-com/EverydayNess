package org.evd.game.runtime.config;

import io.netty.util.internal.StringUtil;
import org.evd.game.annotation.NodeType;
import org.evd.game.runtime.netty.AddressInfo;

import java.util.List;

public class NodeInfo {
    private String name;
    private String addr;
    private NodeType nodeType;
    /** 默认保持现有独立 DBService 路由。 */
    private DbTopology dbTopology = DbTopology.REMOTE_SERVICE;
    /** 链路优先级：低层级主动连接高层级；同层级再按 nodeId 比较。 */
    private int linkLevel;
    private AddressInfo addressInfo;
    private List<ScheduleInfo> schedule;

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

    public DbTopology getDbTopology() {
        return dbTopology;
    }

    public void setDbTopology(DbTopology dbTopology) {
        this.dbTopology = dbTopology;
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
        return localNode.getName().compareTo(remoteNode.getName()) > 0;
    }
}
