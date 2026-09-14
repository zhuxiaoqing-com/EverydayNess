package org.evd.game.common.proxy.MatchService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRequest;
import org.evd.game.common.serializeBean.MatchService.match.SMatchTeamRequest;

/**
* 根据MatchRpcService生成的代理类
*/
public final class MatchRpcProxy {

    private static final MatchRpcProxy INSTANCE = new MatchRpcProxy();

    private MatchRpcProxy() {
    }

    public static MatchRpcProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_MATCHRPC_CANCEL_0 = 0;
        public final static int ENUM_MATCHRPC_CANCELTEAM_1 = 1;
        public final static int ENUM_MATCHRPC_GETMATCHPLAYERNUM_2 = 2;
        public final static int ENUM_MATCHRPC_MATCH_3 = 3;
        public final static int ENUM_MATCHRPC_TEAMMATCH_4 = 4;
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCancel(CallPoint remote, long playerId){
        return RpcResult.run(() -> inst().cancel(remote, playerId));
    }

    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> sendCancelTeam(CallPoint remote, long teamId, long leaderId){
        return RpcResult.run(() -> inst().cancelTeam(remote, teamId, leaderId));
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Integer> callGetMatchPlayerNum(CallPoint remote, int matchType, int mapCfgId){
        return RpcResult.call(() -> inst().getMatchPlayerNum(remote, matchType, mapCfgId));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callMatch(CallPoint remote, SMatchRequest request){
        return RpcResult.call(() -> inst().match(remote, request));
    }


    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<Boolean> callTeamMatch(CallPoint remote, SMatchTeamRequest request){
        return RpcResult.call(() -> inst().teamMatch(remote, request));
    }



    /**
    * 对应源方法: org.evd.game.MatchService.MatchRpc#cancel()
    */
    public void cancel(CallPoint remote, long playerId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.MATCH);
        }
        service.call(remote, EnumCall.ENUM_MATCHRPC_CANCEL_0, new Object[]{playerId});
    }


    /**
    * 对应源方法: org.evd.game.MatchService.MatchRpc#cancelTeam()
    */
    public void cancelTeam(CallPoint remote, long teamId, long leaderId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.MATCH);
        }
        service.call(remote, EnumCall.ENUM_MATCHRPC_CANCELTEAM_1, new Object[]{teamId, leaderId});
    }


    /**
    * 对应源方法: org.evd.game.MatchService.MatchRpc#getMatchPlayerNum()
    */
    public int getMatchPlayerNum(CallPoint remote, int matchType, int mapCfgId){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.MATCH);
        }
        return (int)service.callWait(remote, EnumCall.ENUM_MATCHRPC_GETMATCHPLAYERNUM_2, new Object[]{matchType, mapCfgId});
    }


    /**
    * 对应源方法: org.evd.game.MatchService.MatchRpc#match()
    */
    public boolean match(CallPoint remote, SMatchRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.MATCH);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_MATCHRPC_MATCH_3, new Object[]{request});
    }


    /**
    * 对应源方法: org.evd.game.MatchService.MatchRpc#teamMatch()
    */
    public boolean teamMatch(CallPoint remote, SMatchTeamRequest request){
        Service service = Service.getCurrent();
        if (remote == null) {
            remote = service.getNode().getAnyCallPointByType(ServiceType.MATCH);
        }
        return (boolean)service.callWait(remote, EnumCall.ENUM_MATCHRPC_TEAMMATCH_4, new Object[]{request});
    }


}
