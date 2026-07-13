package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

@SerializeClass
public class NodeServiceStatus implements ISerializable {
    @SerializeField
    private String serviceId;
    @SerializeField
    private int readyContinuations;

    public NodeServiceStatus() {
    }

    public NodeServiceStatus(String serviceId, int readyContinuations) {
        this.serviceId = serviceId;
        this.readyContinuations = readyContinuations;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public int getReadyContinuations() {
        return readyContinuations;
    }

    public void setReadyContinuations(int readyContinuations) {
        this.readyContinuations = readyContinuations;
    }
}
