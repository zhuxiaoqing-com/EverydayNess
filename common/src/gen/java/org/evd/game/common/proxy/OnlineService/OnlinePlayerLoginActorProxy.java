package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.common.proto.C2S_SelectRoleEnter;

/**
* 根据OnlinePlayerLoginActorService生成的代理类
*/
public final class OnlinePlayerLoginActorProxy {

    private static final OnlinePlayerLoginActorProxy INSTANCE = new OnlinePlayerLoginActorProxy();

    private OnlinePlayerLoginActorProxy() {
    }

    public static OnlinePlayerLoginActorProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINEPLAYERLOGINACTOR_SELECTROLEENTER_3 = 3;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendSelectRoleEnter(CallPoint remote, ClientSessionRef session, C2S_SelectRoleEnter request){
        return RpcResult.run(() -> inst().selectRoleEnter(remote, session, request));
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.login.OnlinePlayerLoginActor#selectRoleEnter()
    */
    public void selectRoleEnter(CallPoint remote, ClientSessionRef session, C2S_SelectRoleEnter request){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_ONLINEPLAYERLOGINACTOR_SELECTROLEENTER_3, new Object[]{session, request});
    }


}
