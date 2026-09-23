package org.evd.game.common.constant;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Service;

/** MatchService 路由查询。 */
public final class MatchConst {
    private MatchConst() {
    }

    /** 返回当前 Service 已完成初始化数据同步的 MatchService。 */
    public static CallPoint getMatchCallPoint() {
        return Service.getCurrent().getAnyCallPointByType(ServiceType.MATCH);
    }
}
