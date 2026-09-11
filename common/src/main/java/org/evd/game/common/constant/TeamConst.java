package org.evd.game.common.constant;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/** TeamService 路由。 */
public final class TeamConst {
    private TeamConst() {
    }

    public static CallPoint getTeamCallPoint() {
        return Service.getCurrent().getNode().getAnyCallPointByType(ServiceType.TEAM);
    }
}
