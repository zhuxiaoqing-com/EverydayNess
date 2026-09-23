package org.evd.game.common.proxy.LocationService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallServiceInitDataSync;
import java.util.List;
import org.evd.game.common.serializeBean.LocationService.SLocationAddress;

/**
* 根据LocationServiceConnectRpcService生成的代理类
*/
public final class LocationServiceConnectRpcProxy {

    private static final LocationServiceConnectRpcProxy INSTANCE = new LocationServiceConnectRpcProxy();

    private LocationServiceConnectRpcProxy() {
    }

    public static LocationServiceConnectRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_LOCATIONSERVICECONNECTRPC_ADDBATCH_5 = 5;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendAddBatch(CallPoint remote, CallServiceInitDataSync syncData, List<SLocationAddress> addresses){
        return RpcResult.run(() -> inst().addBatch(remote, syncData, addresses));
    }


    /**
    * 对应源方法: org.evd.game.LocationService.disconnect.LocationServiceConnectRpc#addBatch()
    */
    public void addBatch(CallPoint remote, CallServiceInitDataSync syncData, List<SLocationAddress> addresses){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getAnyCallPointByType(ServiceType.LOC);
        }
        service.call(remote, EnumCall.ENUM_LOCATIONSERVICECONNECTRPC_ADDBATCH_5, new Object[]{syncData, addresses});
    }


}
