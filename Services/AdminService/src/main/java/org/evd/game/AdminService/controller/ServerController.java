package org.evd.game.AdminService.controller;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.AdminService.AdminService;
import org.evd.game.AdminService.http.*;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallServiceStopResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色管理 HTTP 接口。
 */
@HttpRoute("/server")
@Slf4j
public class ServerController {

    @HttpRoute(value = "/stop", type = RequestType.GET)
    public HttpResult<Void> serverStop(HttpRequest ctx) {
        AdminService adminService = ctx.getService(AdminService.class);

        ConcurrentHashMap<CallPoint, List<RegisteredService>> remoteNodeServices = adminService.getNode().getRemoteNodeServices();
        List<RegisteredService> list = remoteNodeServices.values().stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparingInt(a -> ServiceType.shutdownOrderId(a.getServiceType())))
                .toList();

        long timeoutMill = 120_000; // 两分钟超时
        StringJoiner stopMessages = new StringJoiner(System.lineSeparator() + System.lineSeparator());
        for (RegisteredService registeredService : list) {
            // 跳过不是game的服务器
            if(!registeredService.getServiceType().isGame()) {
                continue;
            }
            RpcResult<CallServiceStopResult> rpcResult = adminService.callRemoteRpcServiceStop(registeredService.getCallPoint(), timeoutMill);
            if(!rpcResult.isSuccess()) {
                String errorMessage = String.format("停服错误！！！ service %s errorId %d message %s",
                        registeredService, rpcResult.getErrorCode(), rpcResult.getErrorMessage());
                log.error("{}", errorMessage);
                stopMessages.add(errorMessage);
                return HttpResult.fail(stopMessages.toString());
            }
            CallServiceStopResult value = rpcResult.getValue();
            String successMessage = String.format("停服结束 service %s message %s",
                    registeredService, value.getErrorMessage());
            log.info("{}", successMessage);
            stopMessages.add(successMessage);
        }

        return HttpResult.ok(stopMessages.toString());
    }
}
