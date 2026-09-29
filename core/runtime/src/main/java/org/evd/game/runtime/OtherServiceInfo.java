package org.evd.game.runtime;

import lombok.Getter;
import lombok.Setter;
import org.evd.game.runtime.ymlconfig.RegisteredService;

/** Service 对一个其他 Service 的单连接生命周期状态。 */
@Getter
final class OtherServiceInfo {
    private final RegisteredService service;
    private final long connectTime;
    /** 所属 Node 发布的连接代次；进程内连接使用 Service 实例代次。 */
    private final long ownerNodeSessionId;
    /** 对方发来的初始化数据已经在本地 Service 线程应用完成。 */
    @Setter
    private boolean initDataSync;
    @Setter
    private long lastErrorLogTime;

    OtherServiceInfo(RegisteredService service, long connectTime) {
        this.service = service;
        this.connectTime = connectTime;
        this.ownerNodeSessionId = service.getOwnerNodeSessionId() != 0L
                ? service.getOwnerNodeSessionId()
                : service.getServiceInstanceId();
    }

}
