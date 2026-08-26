package org.evd.game.runtime.call;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

import java.util.Objects;

@SerializeClass
public class CallPoint implements ISerializable {
    public String nodeId;
    public String servId;

    public CallPoint(){

    }

    /**
     * 构造函数
     * @param nodeId
     * @param servId
     */
    public CallPoint(String nodeId, String servId) {
        this.nodeId = nodeId;
        this.servId = servId;
    }

    public CallPoint(CallPoint callPoint) {
        this.nodeId = callPoint.nodeId;
        this.servId = callPoint.servId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("nodeId", nodeId)
                .append("servId", servId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CallPoint callPoint = (CallPoint) o;
        return Objects.equals(nodeId, callPoint.nodeId) && Objects.equals(servId, callPoint.servId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, servId);
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getServId() {
        return servId;
    }

    public void setServId(String servId) {
        this.servId = servId;
    }
}
