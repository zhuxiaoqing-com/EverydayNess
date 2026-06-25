package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.runtime.config.RegisteredService;

import java.util.ArrayList;
import java.util.List;

@SerializeClass
public class CallNodeServicesSync extends CallBase {
    @SerializeField
    private long version;
    @SerializeField
    private List<RegisteredService> services = new ArrayList<>();

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<RegisteredService> getServices() {
        return services;
    }

    public void setServices(List<RegisteredService> services) {
        this.services = services;
    }
}
