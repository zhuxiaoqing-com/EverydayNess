package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import java.util.List;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.runtime.call.CallPoint;
import java.util.Map;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;

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
    public static RpcResult<List<ConnStateCheck>> callReconcileConnSessions(CallPoint remote, CallPoint connService, Map<String,ConnStateCheck> entries){
        return RpcResult.call(() -> inst().reconcileConnSessions(remote, connService, entries));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<PlayerStateCheck[]> callReconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<PlayerStateCheck> entries){
        return RpcResult.call(() -> inst().reconcilePlayerSessions(remote, playerService, entries));
    }



    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileRpc#reconcileConnSessions()
    */
    @SuppressWarnings("unchecked")
    public List<ConnStateCheck> reconcileConnSessions(CallPoint remote, CallPoint connService, Map<String,ConnStateCheck> entries){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (List<ConnStateCheck>)service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILERPC_RECONCILECONNSESSIONS_5, new Object[]{connService, entries});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileRpc#reconcilePlayerSessions()
    */
    public PlayerStateCheck[] reconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<PlayerStateCheck> entries){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        return (PlayerStateCheck[])service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILERPC_RECONCILEPLAYERSESSIONS_6, new Object[]{playerService, entries});
    }


}
