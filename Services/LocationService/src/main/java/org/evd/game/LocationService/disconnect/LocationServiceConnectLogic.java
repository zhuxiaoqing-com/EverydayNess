package org.evd.game.LocationService.disconnect;

import org.evd.game.LocationService.LocationService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;
import org.evd.game.runtime.Service;

import java.util.List;

/** LocationService 接收关联服务重连后的地址恢复数据。 */
@Actor
public final class LocationServiceConnectLogic {
    public void addBatch(List<SLocationAddress> addresses) {
        Service.getCurrent(LocationService.class).addBatch(addresses);
    }
}
