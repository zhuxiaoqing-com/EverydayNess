package org.evd.game.common.constant;

import org.evd.game.annotation.node.NodeType;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.NodeInfo;
import org.evd.game.runtime.ymlconfig.NodeYml;
import org.evd.game.runtime.ymlconfig.ScheduleInfo;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

/** MatchService 的全局节点路由。 */
public final class MatchConst {
    private MatchConst() {
    }

    /** 从启动配置解析唯一的 Global MatchService 实例。 */
    public static CallPoint getMatchCallPoint() {
        NodeYml config = GlobalYml.requireNodeConfig();
        for (NodeInfo node : config.getNodes()) {
            if (node.getNodeType() != NodeType.GLOBAL || node.getSchedule() == null) {
                continue;
            }
            for (ScheduleInfo schedule : node.getSchedule()) {
                if (schedule.getServices() == null) {
                    continue;
                }
                for (ServiceInfo service : schedule.getServices()) {
                    if (service.getServiceType() == ServiceType.MATCH) {
                        return new CallPoint(config.getPlatformId(), config.getServerId(),
                                node.getNodeId(), GlobalYml.getServiceName(service, 1));
                    }
                }
            }
        }
        throw new SysException("没有配置 Global MatchService");
    }
}
