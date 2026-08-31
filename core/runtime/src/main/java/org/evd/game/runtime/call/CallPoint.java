package org.evd.game.runtime.call;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.Objects;

@SerializeClass
public class CallPoint implements ISerializable {
    public int platformId;
    public int serverId;
    public int nodeId;
    public String servId;

    public CallPoint(){

    }

    /**
     * 构造函数
     * @param platformId 平台 ID
     * @param serverId 服务器 ID
     * @param nodeId Node ID
     * @param servId Service ID
     */
    public CallPoint(int platformId, int serverId, int nodeId, String servId) {
        this.platformId = platformId;
        this.serverId = serverId;
        this.nodeId = nodeId;
        this.servId = servId;
    }

    public CallPoint(CallPoint callPoint) {
        this.platformId = callPoint.platformId;
        this.serverId = callPoint.serverId;
        this.nodeId = callPoint.nodeId;
        this.servId = callPoint.servId;
    }

    public CallPoint nodePoint() {
        return new CallPoint(platformId, serverId, nodeId, null);
    }

    public boolean sameNode(CallPoint other) {
        return other != null
                && platformId == other.platformId
                && serverId == other.serverId
                && nodeId == other.nodeId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("platformId", platformId)
                .append("serverId", serverId)
                .append("nodeId", nodeId)
                .append("servId", servId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CallPoint callPoint = (CallPoint) o;
        return platformId == callPoint.platformId
                && serverId == callPoint.serverId
                && nodeId == callPoint.nodeId
                && Objects.equals(servId, callPoint.servId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformId, serverId, nodeId, servId);
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

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public String getServId() {
        return servId;
    }

    public void setServId(String servId) {
        this.servId = servId;
    }
}
