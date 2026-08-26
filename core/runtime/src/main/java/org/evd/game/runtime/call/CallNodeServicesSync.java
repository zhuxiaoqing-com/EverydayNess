package org.evd.game.runtime.call;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.List;

@SerializeClass
public class CallNodeServicesSync extends CallBase {
    private boolean init;
    private String addr;
    private List<RegisteredService> services = new ArrayList<>();

    public boolean isInit() {
        return init;
    }

    public void setInit(boolean init) {
        this.init = init;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public List<RegisteredService> getServices() {
        return services;
    }

    public void setServices(List<RegisteredService> services) {
        this.services = services;
    }


    @Override
    public String toString() {
        return "CallNodeServicesSync{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", services=" + services +
                ", addr='" + addr + '\'' +
                ", init=" + init +
                '}';
    }
}
