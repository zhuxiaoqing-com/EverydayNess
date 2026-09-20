package org.evd.game.common.proxy.TeamService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据TeamOfflineRpcService生成的代理类
*/
public final class TeamOfflineRpcProxy {

    private static final TeamOfflineRpcProxy INSTANCE = new TeamOfflineRpcProxy();

    private TeamOfflineRpcProxy() {
    }

    public static TeamOfflineRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_TEAMOFFLINERPC_ONPLAYEROFFLINE_2 = 2;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendOnPlayerOffline(CallPoint remote, long playerId, CallPoint playerService){
        return RpcResult.run(() -> inst().onPlayerOffline(remote, playerId, playerService));
    }


    /**
    * 对应源方法: org.evd.game.TeamService.TeamOfflineRpc#onPlayerOffline()
    */
    public void onPlayerOffline(CallPoint remote, long playerId, CallPoint playerService){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.TEAM);
        }
        service.call(remote, EnumCall.ENUM_TEAMOFFLINERPC_ONPLAYEROFFLINE_2, new Object[]{playerId, playerService});
    }


}
