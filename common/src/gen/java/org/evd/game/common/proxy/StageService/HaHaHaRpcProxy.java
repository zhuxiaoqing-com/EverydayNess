package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.annotation.ActorType;

/**
* 根据HaHaHaRpcService生成的代理类
*/
public final class HaHaHaRpcProxy {

    private static final HaHaHaRpcProxy INSTANCE = new HaHaHaRpcProxy();

    private HaHaHaRpcProxy() {
    }

    public static HaHaHaRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_HAHAHARPC_RPC1_0 = 0;
        public final static int ENUM_HAHAHARPC_RPC2_1 = 1;
        public final static int ENUM_HAHAHARPC_RPC3_2 = 2;
        public final static int ENUM_HAHAHARPC_RPC4_3 = 3;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> callRpc1(long actorUniqueId, int a, int b){
        return RpcResult.run(() -> inst().rpc1(actorUniqueId, a, b));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> callRpc2(long actorUniqueId, Object a, Object b){
        return RpcResult.run(() -> inst().rpc2(actorUniqueId, a, b));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRpc3(CallPoint remote, Object a, Object b){
        return RpcResult.run(() -> inst().rpc3(remote, a, b));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> callRpc4(long actorUniqueId, Object a, Object b){
        return RpcResult.run(() -> inst().rpc4(actorUniqueId, a, b));
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaRpc#rpc1()
    */
    public void rpc1(long actorUniqueId, int a, int b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHARPC_RPC1_0, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaRpc#rpc2()
    */
    public void rpc2(long actorUniqueId, Object a, Object b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHARPC_RPC2_1, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaRpc#rpc3()
    */
    public void rpc3(CallPoint remote, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHARPC_RPC3_2, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.HaHaHaRpc#rpc4()
    */
    public void rpc4(long actorUniqueId, Object a, Object b){
        ActorId actorId = new ActorId(ActorType.MAP_PLAYER, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.ENUM_HAHAHARPC_RPC4_3, new Object[]{a, b});
    }


}
