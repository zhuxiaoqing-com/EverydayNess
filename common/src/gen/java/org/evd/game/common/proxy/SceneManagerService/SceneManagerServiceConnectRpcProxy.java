package org.evd.game.common.proxy.SceneManagerService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallPoint;
import java.util.List;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

/**
* 根据SceneManagerServiceConnectRpcService生成的代理类
*/
public final class SceneManagerServiceConnectRpcProxy {

    private static final SceneManagerServiceConnectRpcProxy INSTANCE = new SceneManagerServiceConnectRpcProxy();

    private SceneManagerServiceConnectRpcProxy() {
    }

    public static SceneManagerServiceConnectRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_SCENEMANAGERSERVICECONNECTRPC_RESTORESTAGEMAPS_5 = 5;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendRestoreStageMaps(CallPoint remote, CallPoint stage, List<SMapInfo> maps){
        return RpcResult.run(() -> inst().restoreStageMaps(remote, stage, maps));
    }


    /**
    * 对应源方法: org.evd.game.SceneManagerService.disconnect.SceneManagerServiceConnectRpc#restoreStageMaps()
    */
    public void restoreStageMaps(CallPoint remote, CallPoint stage, List<SMapInfo> maps){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.SCENE_MANAGER);
        }
        service.call(remote, EnumCall.ENUM_SCENEMANAGERSERVICECONNECTRPC_RESTORESTAGEMAPS_5, new Object[]{stage, maps});
    }


}
