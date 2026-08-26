package org.evd.game.runtime.call;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

@SerializeClass
public class NodeServiceStatus implements ISerializable {
    private String serviceId;
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
