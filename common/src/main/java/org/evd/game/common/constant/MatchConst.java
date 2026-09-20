package org.evd.game.common.constant;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Service;

/** MatchService 的全局节点路由。 */
public final class MatchConst {
    private MatchConst() {
    }

    /** 返回当前 Node 路由中已连接的 MatchService。 */
    public static CallPoint getMatchCallPoint() {
        return Service.getCurrent().getNode().getAnyCallPointByType(ServiceType.MATCH);
    }
}
