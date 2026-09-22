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
    private int platformId;
    private int serverId;
    private int nodeId;
    /**
     * Service 所在 Node 与目标 Node 之间的连接 ID。
     *
     * 例如：AService 属于 ANode，A → B，
     * 这里保存的是 ANode 上连接 BNode 的 channelId。
     */
    private long channelId;

    /** 本地记录的离线时间，不参与服务注册信息序列化。 */
    @SerializeIgnore
    private long offlineMill;

    /** 本地记录 Service 进入 Pending 的时间，不参与服务注册信息序列化。 */
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

    public CallPoint getCallPoint() {
        if(callPoint == null) {
            callPoint = new CallPoint(platformId, serverId, nodeId, serviceId);
        }
        return callPoint;
    }


}
