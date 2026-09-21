package org.evd.game.LocationService.disconnect;

import org.evd.game.LocationService.LocationService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;

/** LocationService 关联服务断开后的地址和定位锁清理逻辑。 */
@Actor
public final class LocationServiceDisconnectLogic {
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        LocationService owner = owner();
        for (RegisteredService service : serviceList) {
            if (service == null || service.getCallPoint() == null) {
                continue;
            }
            owner.cleanupDisconnectedService(service.getCallPoint());
        }
    }

    private LocationService owner() {
        return Service.getCurrent(LocationService.class);
    }
}
