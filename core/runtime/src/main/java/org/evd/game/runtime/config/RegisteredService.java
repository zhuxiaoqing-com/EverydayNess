package org.evd.game.runtime.config;

import org.evd.game.annotation.ServiceType;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeIgnore;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@SerializeClass
public class RegisteredService implements ISerializable {
    private ServiceType serviceType;
    private String serviceClassName;
    private String serviceId;
    private String nodeId;

    @SerializeIgnore
    private CallPoint callPoint;

    public RegisteredService() {
    }

    public RegisteredService(ServiceType serviceType, String serviceClassName, String serviceId, String nodeId) {
        this.serviceType = serviceType;
        this.serviceClassName = serviceClassName;
        this.serviceId = serviceId;
        this.nodeId = nodeId;
    }

    public RegisteredService(RegisteredService other) {
        this(other.serviceType, other.serviceClassName, other.serviceId, other.nodeId);
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

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public CallPoint getCallPoint() {
        if(callPoint == null) {
            callPoint = new CallPoint(nodeId, serviceId);
        }
        return callPoint;
    }

    public void setCallPoint(CallPoint callPoint) {
        this.callPoint = callPoint;
    }


    @Override
    public String toString() {
        return "RegisteredService{" +
                "serviceType=" + serviceType +
                ", serviceClassName='" + serviceClassName + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", callPoint=" + callPoint +
                '}';
    }
}
