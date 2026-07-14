package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

import java.util.ArrayList;
import java.util.List;

@SerializeClass
public class CallPing extends CallBase {
    public String addr;
    private List<NodeServiceStatus> serviceStatuses = new ArrayList<>();

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public List<NodeServiceStatus> getServiceStatuses() {
        return serviceStatuses;
    }

    public void setServiceStatuses(List<NodeServiceStatus> serviceStatuses) {
        this.serviceStatuses = serviceStatuses;
    }

    @Override
    public String toString() {
        return "CallPing{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", addr='" + addr + '\'' +
                ", serviceStatuses=" + serviceStatuses +
                '}';
    }
}
