package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.OnlineService.reconcile.ConnStateCheck;
import org.evd.game.runtime.call.CallPoint;
import java.util.List;
import org.evd.game.common.serializeBean.OnlineService.reconcile.PlayerStateCheck;

/**
* 根据OnlineStateReconcileActorService生成的代理类
*/
public final class OnlineStateReconcileActorProxy {

    private static final OnlineStateReconcileActorProxy INSTANCE = new OnlineStateReconcileActorProxy();

    private OnlineStateReconcileActorProxy() {
    }

    public static OnlineStateReconcileActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINESTATERECONCILEACTOR_RECONCILECONNSESSIONS_5 = 5;
        public final static int ENUM_ONLINESTATERECONCILEACTOR_RECONCILEPLAYERSESSIONS_6 = 6;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<ConnStateCheck[]> callReconcileConnSessions(CallPoint remote, CallPoint connService, List<ConnStateCheck> entries){
        return RpcResult.call(() -> inst().reconcileConnSessions(remote, connService, entries));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<PlayerStateCheck[]> callReconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<PlayerStateCheck> entries){
        return RpcResult.call(() -> inst().reconcilePlayerSessions(remote, playerService, entries));
    }



    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileActor#reconcileConnSessions()
    */
    public ConnStateCheck[] reconcileConnSessions(CallPoint remote, CallPoint connService, List<ConnStateCheck> entries){
        Service service = Service.getCurrent();
        return (ConnStateCheck[])service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILEACTOR_RECONCILECONNSESSIONS_5, new Object[]{connService, entries});
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.reconcile.OnlineStateReconcileActor#reconcilePlayerSessions()
    */
    public PlayerStateCheck[] reconcilePlayerSessions(CallPoint remote, CallPoint playerService, List<PlayerStateCheck> entries){
        Service service = Service.getCurrent();
        return (PlayerStateCheck[])service.callWait(remote, EnumCall.ENUM_ONLINESTATERECONCILEACTOR_RECONCILEPLAYERSESSIONS_6, new Object[]{playerService, entries});
    }


}
