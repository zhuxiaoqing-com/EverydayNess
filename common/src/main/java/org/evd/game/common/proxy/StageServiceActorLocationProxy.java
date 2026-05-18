package org.evd.game.common.proxy;

import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/**
 * 手写 actor-location 专用代理，避免依赖本轮后生成的 StageServiceProxy。
 * 这里的 methodKey 对应 StageService 里的 owner @Rpc 方法排序。
 */
public class StageServiceActorLocationProxy extends RPCProxyBase {
    private static final int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT = 0;
    private static final int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT = 1;

    private StageServiceActorLocationProxy(CallPoint callPoint) {
        this.remote = callPoint;
    }

    public static StageServiceActorLocationProxy inst(CallPoint callPoint) {
        return new StageServiceActorLocationProxy(callPoint);
    }

    public void callHaHaHaActorRpc1(long actorId, int a, int b) {
        Service service = Service.getCurrent();
        service.call(remote, ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT, new Object[]{actorId, a, b});
    }

    public void callHaHaHaActorRpc2(long actorId, Object a, Object b) {
        Service service = Service.getCurrent();
        service.call(remote, ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT, new Object[]{actorId, a, b});
    }
}
