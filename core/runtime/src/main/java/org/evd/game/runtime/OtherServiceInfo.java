package org.evd.game.runtime;

import lombok.Getter;
import lombok.Setter;
import org.evd.game.runtime.ymlconfig.RegisteredService;

/** Service 对一个其他 Service 的单连接生命周期状态。 */
@Getter
public final class OtherServiceInfo {
    private final RegisteredService service;
    private final long connectTime;
    /** 连接代次；远端使用 Node Session，进程内连接使用 Service 实例代次。 */
    private final long sessionId;
    @Setter
    private boolean ready;
    /** 对方发来的初始化数据已经在本地 Service 线程应用完成。 */
    @Setter
    private boolean initDataSync;
    @Setter
    private long lastErrorLogTime;

    public OtherServiceInfo(RegisteredService service, long connectTime) {
        this.service = service;
        this.connectTime = connectTime;
        this.sessionId = service.getSessionId() != 0L
                ? service.getSessionId()
                : service.getServiceInstanceId();
    }

    public boolean isReadyAndSync() {
        return ready && initDataSync;
    }

}
