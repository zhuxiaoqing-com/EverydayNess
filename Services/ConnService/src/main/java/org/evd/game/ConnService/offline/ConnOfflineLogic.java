package org.evd.game.ConnService.offline;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Service;

/** ConnService 的离线业务逻辑。 */
@Actor
public final class ConnOfflineLogic {
    /** 按断开类型关闭指定网关会话，并触发离线通知。 */
    public void kickSession(long sessionId, int brokenTypeCode, String reason) {
        owner().offlineManager().kickSession(sessionId, brokenTypeCode, reason);
    }

    /** 统一执行网关连接关闭、离线通知和资源清理。 */
    public void closeSession(long sessionId, int brokenTypeCode, String reason) {
        owner().offlineManager().closeSession(sessionId, brokenTypeCode, reason);
    }

    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
