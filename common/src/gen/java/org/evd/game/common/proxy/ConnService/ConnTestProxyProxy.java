package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;
import org.evd.game.common.serializeBean.ConnService.ConnInfo;

/**
* 根据ConnTestProxyService生成的代理类
*/
public final class ConnTestProxyProxy {

    private static final ConnTestProxyProxy INSTANCE = new ConnTestProxyProxy();

    private ConnTestProxyProxy() {
    }

    public static ConnTestProxyProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNTESTPROXY_CONNTEST2_6 = 6;
        public final static int ENUM_CONNTESTPROXY_CONNTEST3_7 = 7;
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callConnTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        return RpcResult.run(() -> {
            ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
            Service.getCurrent().getMessageLocationSender().callWait(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST2_6, new Object[]{a, b, connInfo});
        });
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callConnTest3(long actorUniqueId){
        return RpcResult.run(() -> {
            ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
            Service.getCurrent().getMessageLocationSender().callWait(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST3_7, new Object[]{});
        });
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public void connTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST2_6, new Object[]{a, b, connInfo});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest3()
    */
    public void connTest3(long actorUniqueId){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST3_7, new Object[]{});
    }


}
