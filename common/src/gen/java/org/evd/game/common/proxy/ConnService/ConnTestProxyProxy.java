package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;
import org.evd.game.common.serializeBean.ConnService.test.ConnInfo;

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
        public final static int ENUM_CONNTESTPROXY_CONNTEST1_6 = 6;
        public final static int ENUM_CONNTESTPROXY_CONNTEST2_7 = 7;
        public final static int ENUM_CONNTESTPROXY_CONNTEST3_8 = 8;
        public final static int ENUM_CONNTESTPROXY_CONNTEST4_9 = 9;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> callConnTest1(long actorUniqueId){
        return RpcResult.run(() -> inst().connTest1(actorUniqueId));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callConnTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        return RpcResult.call(() -> inst().connTest2(actorUniqueId, a, b, connInfo));
    }


    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendConnTest3(CallPoint remote){
        return RpcResult.run(() -> inst().connTest3(remote));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callConnTest4(CallPoint remote, int a, Object b, ConnInfo connInfo){
        return RpcResult.call(() -> inst().connTest4(remote, a, b, connInfo));
    }



    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest1()
    */
    public void connTest1(long actorUniqueId){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST1_6, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public boolean connTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        return (boolean)Service.getCurrent().getMessageLocationSender().callWait(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST2_7, new Object[]{a, b, connInfo});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest3()
    */
    public void connTest3(CallPoint remote){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNTESTPROXY_CONNTEST3_8, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest4()
    */
    public boolean connTest4(CallPoint remote, int a, Object b, ConnInfo connInfo){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNTESTPROXY_CONNTEST4_9, new Object[]{a, b, connInfo});
    }


}
