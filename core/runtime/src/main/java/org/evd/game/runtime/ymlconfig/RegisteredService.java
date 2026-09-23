package org.evd.game.runtime.ymlconfig;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.annotation.serialize.SerializeIgnore;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.call.CallPoint;

@Setter
@Getter
@ToString
@SerializeClass
public class RegisteredService implements ISerializable {
    private ServiceType serviceType;
    private String serviceClassName;
    private String serviceId;
    /** 本次 Service 启动实例的唯一 ID，不参与路由。 */
    private long serviceInstanceId;
    /** 本次 Service 所属 Node 连接到对端 Node 的连接代次。 */
    private long sessionId;
    private int platformId;
    private int serverId;
    private int nodeId;
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
        this.serviceInstanceId = other.serviceInstanceId;
        this.sessionId = other.sessionId;
        this.callPoint = other.callPoint == null ? null : new CallPoint(other.callPoint);
    }

    public CallPoint getCallPoint() {
        if(callPoint == null) {
            callPoint = new CallPoint(platformId, serverId, nodeId, serviceId);
        }
        return callPoint;
    }

    /** 判断是否已经不是同一个 Service 实例或 Node 连接。 */
    public boolean isDifferentServiceSession(RegisteredService other) {
        return other == null
                || serviceInstanceId != other.serviceInstanceId
                || sessionId != other.sessionId;
    }


}
