package org.evd.game.LobbyService.routing;

import org.evd.game.LobbyService.LobbyService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.proxy.ConnService.ConnServiceProxy;
import org.evd.game.common.proxy.PlayerService.PlayerServiceProxy;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;

import java.util.List;

@Actor
public final class LobbyLoadBalancerActor {
    public LobbyConnCandidate selectLeastLoadedConn() {
        LobbyService owner = owner();
        List<RegisteredService> services = owner.getNode().getServicesByType(ServiceType.CONN);
        LobbyConnCandidate best = null;
        for (RegisteredService service : services) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<String> publicAddrResult = ConnServiceProxy.callGetPublicAddr(callPoint);
            if (!publicAddrResult.isSuccess()) {
                LogCore.core.warn("LobbyService 获取 ConnService 公网地址失败: callPoint={}, errorCode={}, message={}",
                        callPoint, publicAddrResult.getErrorCode(), publicAddrResult.getErrorMessage());
                continue;
            }
            String publicAddr = publicAddrResult.getValue();
            if (publicAddr == null || publicAddr.isBlank()) {
                continue;
            }
            RpcResult<Integer> loginCountResult = ConnServiceProxy.callGetLoginSessionCount(callPoint);
            if (!loginCountResult.isSuccess()) {
                LogCore.core.warn("LobbyService 获取 ConnService 登录数失败: callPoint={}, errorCode={}, message={}",
                        callPoint, loginCountResult.getErrorCode(), loginCountResult.getErrorMessage());
                continue;
            }
            int loginCount = loginCountResult.getValue();
            if (best == null || loginCount < best.loginCount()) {
                best = new LobbyConnCandidate(new CallPoint(callPoint), publicAddr, loginCount);
            }
        }
        return best;
    }

    public LobbyPlayerCandidate selectLeastLoadedPlayer() {
        LobbyService owner = owner();
        List<RegisteredService> services = owner.getNode().getServicesByType(ServiceType.PLAYER);
        LobbyPlayerCandidate best = null;
        for (RegisteredService service : services) {
            CallPoint callPoint = service.getCallPoint();
            RpcResult<Integer> onlineCountResult = PlayerServiceProxy.callGetOnlineCount(callPoint);
            if (!onlineCountResult.isSuccess()) {
                LogCore.core.warn("LobbyService 获取 PlayerService 在线数失败: callPoint={}, errorCode={}, message={}",
                        callPoint, onlineCountResult.getErrorCode(), onlineCountResult.getErrorMessage());
                continue;
            }
            int onlineCount = onlineCountResult.getValue();
            if (best == null || onlineCount < best.onlineCount()) {
                best = new LobbyPlayerCandidate(new CallPoint(callPoint), onlineCount);
            }
        }
        return best;
    }

    private LobbyService owner() {
        return Service.getCurrent(LobbyService.class);
    }
}
