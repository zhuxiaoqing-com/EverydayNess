package org.evd.game.common.proxy.ConnService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/**
* 根据ConnServiceService生成的代理类
*/
public final class ConnServiceProxy {

    private static final ConnServiceProxy INSTANCE = new ConnServiceProxy();

    private ConnServiceProxy() {
    }

    public static ConnServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNSERVICE_CONFIRMLOGIN_0 = 0;
        public final static int ENUM_CONNSERVICE_GETLOGINSESSIONCOUNT_1 = 1;
        public final static int ENUM_CONNSERVICE_GETPUBLICADDR_2 = 2;
        public final static int ENUM_CONNSERVICE_KICKSESSION_3 = 3;
        public final static int ENUM_CONNSERVICE_PUSHTOCLIENT_4 = 4;
        public final static int ENUM_CONNSERVICE_UPDATEPLAYERBINDING_5 = 5;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callConfirmLogin(CallPoint remote, long sessionId, String userId, long playerId){
        return RpcResult.call(() -> inst().confirmLogin(remote, sessionId, userId, playerId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetLoginSessionCount(CallPoint remote){
        return RpcResult.call(() -> inst().getLoginSessionCount(remote));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<String> callGetPublicAddr(CallPoint remote){
        return RpcResult.call(() -> inst().getPublicAddr(remote));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callKickSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        return RpcResult.call(() -> inst().kickSession(remote, sessionId, brokenTypeCode, reason));
    }


    /**
    * 对应 void RPC 的结果版本；等待远端响应，远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Void> callPushToClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        return RpcResult.run(() -> {
            Service service = Service.getCurrent();
            service.callWait(remote, EnumCall.ENUM_CONNSERVICE_PUSHTOCLIENT_4, new Object[]{sessionId, packet});
        });
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callUpdatePlayerBinding(CallPoint remote, long sessionId, long playerId){
        return RpcResult.call(() -> inst().updatePlayerBinding(remote, sessionId, playerId));
    }



    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#confirmLogin()
    */
    public boolean confirmLogin(CallPoint remote, long sessionId, String userId, long playerId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_CONFIRMLOGIN_0, new Object[]{sessionId, userId, playerId});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#getLoginSessionCount()
    */
    public int getLoginSessionCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_GETLOGINSESSIONCOUNT_1, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#getPublicAddr()
    */
    public String getPublicAddr(CallPoint remote){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_GETPUBLICADDR_2, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#kickSession()
    */
    public boolean kickSession(CallPoint remote, long sessionId, int brokenTypeCode, String reason){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_KICKSESSION_3, new Object[]{sessionId, brokenTypeCode, reason});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#pushToClient()
    */
    public void pushToClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_PUSHTOCLIENT_4, new Object[]{sessionId, packet});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#updatePlayerBinding()
    */
    public boolean updatePlayerBinding(CallPoint remote, long sessionId, long playerId){
        Service service = Service.getCurrent();
        return (boolean)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_UPDATEPLAYERBINDING_5, new Object[]{sessionId, playerId});
    }


}
