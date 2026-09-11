package org.evd.game.common.proxy.TeamService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;

/**
* 根据TeamMatchRpcService生成的代理类
*/
public final class TeamMatchRpcProxy {

    private static final TeamMatchRpcProxy INSTANCE = new TeamMatchRpcProxy();

    private TeamMatchRpcProxy() {
    }

    public static TeamMatchRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_TEAMMATCHRPC_MATCHRESULT_0 = 0;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendMatchResult(CallPoint remote, long teamId, int matchType, boolean success, SMapInfo mapInfo){
        return RpcResult.run(() -> inst().matchResult(remote, teamId, matchType, success, mapInfo));
    }


    /**
    * 对应源方法: org.evd.game.TeamService.TeamMatchRpc#matchResult()
    */
    public void matchResult(CallPoint remote, long teamId, int matchType, boolean success, SMapInfo mapInfo){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.TEAM);
        }
        service.call(remote, EnumCall.ENUM_TEAMMATCHRPC_MATCHRESULT_0, new Object[]{teamId, matchType, success, mapInfo});
    }


}
