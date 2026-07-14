package org.evd.game.common.proxy.LocationService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorAddress;

/**
* 根据LocationServiceService生成的代理类
*/
public final class LocationServiceProxy implements LocationInterface {

    private static final LocationServiceProxy INSTANCE = new LocationServiceProxy();

    private LocationServiceProxy() {
    }

    public static LocationServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOCATIONSERVICE_ADD_0 = 0;
        public final static int ENUM_LOCATIONSERVICE_GET_1 = 1;
        public final static int ENUM_LOCATIONSERVICE_LOCK_2 = 2;
        public final static int ENUM_LOCATIONSERVICE_REMOVE_3 = 3;
        public final static int ENUM_LOCATIONSERVICE_UNLOCK_4 = 4;
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callAdd(CallPoint remote, ActorId actorId, ActorAddress actorAddress){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_ADD_0, new Object[]{actorId, actorAddress});
        });
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<ActorAddress> callGet(CallPoint remote, ActorId actorId){
        return RpcResult.call(() -> inst().get(remote, actorId));
    }


    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callLock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, int timeMillis){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_LOCK_2, new Object[]{actorId, oldActorAddress, timeMillis});
        });
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callRemove(CallPoint remote, ActorId actorId){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_REMOVE_3, new Object[]{actorId});
        });
    }

    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callUnlock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_UNLOCK_4, new Object[]{actorId, oldActorAddress, newActorAddress});
        });
    }


    /**
    * 对应源方法: org.evd.game.LocationService.LocationService#add()
    */
    public void add(CallPoint remote, ActorId actorId, ActorAddress actorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_ADD_0, new Object[]{actorId, actorAddress});
    }


    /**
    * 对应源方法: org.evd.game.LocationService.LocationService#get()
    */
    public ActorAddress get(CallPoint remote, ActorId actorId){
        Service service = Service.getCurrent();
        return (ActorAddress)service.callWait(remote, EnumCall.ENUM_LOCATIONSERVICE_GET_1, new Object[]{actorId});
    }


    /**
    * 对应源方法: org.evd.game.LocationService.LocationService#lock()
    */
    public void lock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, int timeMillis){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_LOCK_2, new Object[]{actorId, oldActorAddress, timeMillis});
    }


    /**
    * 对应源方法: org.evd.game.LocationService.LocationService#remove()
    */
    public void remove(CallPoint remote, ActorId actorId){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_REMOVE_3, new Object[]{actorId});
    }


    /**
    * 对应源方法: org.evd.game.LocationService.LocationService#unlock()
    */
    public void unlock(CallPoint remote, ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICE_UNLOCK_4, new Object[]{actorId, oldActorAddress, newActorAddress});
    }


}
