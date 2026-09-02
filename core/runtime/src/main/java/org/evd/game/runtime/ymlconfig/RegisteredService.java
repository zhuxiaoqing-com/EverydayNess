package org.evd.game.runtime.ymlconfig;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.annotation.serialize.SerializeIgnore;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class RegisteredService implements ISerializable {
    private ServiceType serviceType;
    private String serviceClassName;
    private String serviceId;
    private int platformId;
    private int serverId;
    private int nodeId;

    /** 本地记录的离线时间，不参与服务注册信息序列化。 */
    @SerializeIgnore
    private long offlineMill;

    @SerializeIgnore
    private long pendingStartTime;

    @SerializeIgnore
    private CallPoint callPoint;

    public RegisteredService() {
    }

    public RegisteredService(ServiceType serviceType, String serviceClassName, String serviceId,
                             int platformId, int serverId, int nodeId) {
        this.serviceType = serviceType;
        this.serviceClassName = serviceClassName;
        this.serviceId = serviceId;
        this.platformId = platformId;
        this.serverId = serverId;
        this.nodeId = nodeId;
    }

    public RegisteredService(RegisteredService other) {
        this(other.serviceType, other.serviceClassName, other.serviceId,
                other.platformId, other.serverId, other.nodeId);
        this.offlineMill = other.offlineMill;
        this.pendingStartTime = other.pendingStartTime;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }

    public void setServiceClassName(String serviceClassName) {
        this.serviceClassName = serviceClassName;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
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

    public long getOfflineMill() {
        return offlineMill;
    }

    public void setOfflineMill(long offlineMill) {
        this.offlineMill = offlineMill;
    }

    public CallPoint getCallPoint() {
        if(callPoint == null) {
            callPoint = new CallPoint(platformId, serverId, nodeId, serviceId);
        }
        return callPoint;
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint;
    }


    public long getPendingStartTime() {
        return pendingStartTime;
    }

    public void setPendingStartTime(long pendingStartTime) {
        this.pendingStartTime = pendingStartTime;
    }

    @Override
    public String toString() {
        return "RegisteredService{" +
                "serviceType=" + serviceType +
                ", serviceClassName='" + serviceClassName + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", platformId=" + platformId +
                ", serverId=" + serverId +
                ", nodeId=" + nodeId +
                ", callPoint=" + callPoint +
                '}';
    }
}
