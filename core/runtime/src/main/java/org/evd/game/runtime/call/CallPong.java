package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

import java.util.ArrayList;
import java.util.List;

@SerializeClass
public class CallPong extends CallBase {
    private List<NodeServiceStatus> serviceStatuses = new ArrayList<>();

    public List<NodeServiceStatus> getServiceStatuses() {
        return serviceStatuses;
    }

    public void setServiceStatuses(List<NodeServiceStatus> serviceStatuses) {
        this.serviceStatuses = serviceStatuses;
    }

    @Override
    public String toString() {
        return "CallPong{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", serviceStatuses=" + serviceStatuses +
                '}';
    }
}
