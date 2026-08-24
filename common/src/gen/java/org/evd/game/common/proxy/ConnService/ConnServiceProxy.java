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
        public final static int ENUM_CONNSERVICE_GETLOGINSESSIONCOUNT_0 = 0;
        public final static int ENUM_CONNSERVICE_GETPUBLICADDR_1 = 1;
        public final static int ENUM_CONNSERVICE_PUSHTOCLIENT_2 = 2;
        public final static int ENUM_CONNSERVICE_PUSHTOPLAYERID_3 = 3;
        public final static int ENUM_CONNSERVICE_PUSHTOUSERID_4 = 4;
        public final static int ENUM_CONNSERVICE_REDIRECTCLIENT_5 = 5;
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
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendPushToClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        return RpcResult.run(() -> inst().pushToClient(remote, sessionId, packet));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendPushToPlayerId(CallPoint remote, long playerId, ClientFrameChunk packet){
        return RpcResult.run(() -> inst().pushToPlayerId(remote, playerId, packet));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendPushToUserId(CallPoint remote, String userId, ClientFrameChunk packet){
        return RpcResult.run(() -> inst().pushToUserId(remote, userId, packet));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRedirectClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        return RpcResult.run(() -> inst().redirectClient(remote, sessionId, packet));
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#getLoginSessionCount()
    */
    public int getLoginSessionCount(CallPoint remote){
        Service service = Service.getCurrent();
        return (int)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_GETLOGINSESSIONCOUNT_0, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#getPublicAddr()
    */
    public String getPublicAddr(CallPoint remote){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_CONNSERVICE_GETPUBLICADDR_1, new Object[]{});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#pushToClient()
    */
    public void pushToClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_PUSHTOCLIENT_2, new Object[]{sessionId, packet});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#pushToPlayerId()
    */
    public void pushToPlayerId(CallPoint remote, long playerId, ClientFrameChunk packet){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_PUSHTOPLAYERID_3, new Object[]{playerId, packet});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#pushToUserId()
    */
    public void pushToUserId(CallPoint remote, String userId, ClientFrameChunk packet){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_PUSHTOUSERID_4, new Object[]{userId, packet});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnService#redirectClient()
    */
    public void redirectClient(CallPoint remote, long sessionId, ClientFrameChunk packet){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNSERVICE_REDIRECTCLIENT_5, new Object[]{sessionId, packet});
    }


}
