package org.evd.game.common.proxy.OnlineService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import java.util.List;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据OnlineServiceConnectRpcService生成的代理类
*/
public final class OnlineServiceConnectRpcProxy {

    private static final OnlineServiceConnectRpcProxy INSTANCE = new OnlineServiceConnectRpcProxy();

    private OnlineServiceConnectRpcProxy() {
    }

    public static OnlineServiceConnectRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_ONLINESERVICECONNECTRPC_RESTOREHISTORICALPLAYERSERVICES_0 = 0;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRestoreHistoricalPlayerServices(CallPoint remote, List<String> userIds, CallPoint playerService){
        return RpcResult.run(() -> inst().restoreHistoricalPlayerServices(remote, userIds, playerService));
    }


    /**
    * 对应源方法: org.evd.game.OnlineService.disconnect.OnlineServiceConnectRpc#restoreHistoricalPlayerServices()
    */
    public void restoreHistoricalPlayerServices(CallPoint remote, List<String> userIds, CallPoint playerService){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.ONLINE);
        }
        service.call(remote, EnumCall.ENUM_ONLINESERVICECONNECTRPC_RESTOREHISTORICALPLAYERSERVICES_0, new Object[]{userIds, playerService});
    }


}
