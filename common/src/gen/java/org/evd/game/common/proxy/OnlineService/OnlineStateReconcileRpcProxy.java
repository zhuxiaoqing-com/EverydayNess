package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import java.util.List;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SConnStateCheck;
import org.evd.game.runtime.call.CallPoint;
import java.util.Map;
import org.evd.game.common.serializeBean.OnlineService.reconcile.SPlayerStateCheck;

/**
* 根据OnlineStateReconcileRpcService生成的代理类
*/
public final class OnlineStateReconcileRpcProxy {

    private static final OnlineStateReconcileRpcProxy INSTANCE = new OnlineStateReconcileRpcProxy();

    private OnlineStateReconcileRpcProxy() {
    }

    public static OnlineStateReconcileRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINESTATERECONCILERPC_RECONCILECONNSESSIONS_5 = 5;
        public final static int ENUM_ONLINESTATERECONCILERPC_RECONCILEPLAYERSESSIONS_6 = 6;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<List<SConnStateCheck>> callReconcileConnSessions(CallPoint remote, CallPoint connService, Map<String,SConnStateCheck> entries){
        return RpcResult.call(() -> inst().reconcileConnSessions(remote, connService, entries));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<SPlayerStateCheck[]> callReconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<SPlayerStateCheck> entries){
        return RpcResult.call(() -> inst().reconcilePlayerSessions(remote, playerService, entries));
    }



    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileRpc#reconcileConnSessions()
    */
    @SuppressWarnings("unchecked")
    public List<SConnStateCheck> reconcileConnSessions(CallPoint remote, CallPoint connService, Map<String,SConnStateCheck> entries){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (List<SConnStateCheck>)service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILERPC_RECONCILECONNSESSIONS_5, new Object[]{connService, entries});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileRpc#reconcilePlayerSessions()
    */
    public SPlayerStateCheck[] reconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<SPlayerStateCheck> entries){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (SPlayerStateCheck[])service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILERPC_RECONCILEPLAYERSESSIONS_6, new Object[]{playerService, entries});
    }


}
