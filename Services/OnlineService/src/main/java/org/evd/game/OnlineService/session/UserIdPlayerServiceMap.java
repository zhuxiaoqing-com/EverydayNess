package org.evd.game.OnlineService.session;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.common.proxy.PlayerService.PlayerServiceRpcProxy;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OnlineService 中 userId 到 PlayerService 的绑定。
 *
 * <p>历史绑定由 PlayerService 的 MDB 缓存生命周期驱动清理。</p>
 */
public final class UserIdPlayerServiceMap {
    private static final class Binding {
        private CallPoint service;

        private Binding(CallPoint service) {
            this.service = service;
        }

        private CallPoint service() {
            return service;
        }

        private void setService(CallPoint service) {
            this.service = service;
        }

    }

    private final Map<String, Binding> bindings = new HashMap<>();

    /** 建立或刷新用户到 PlayerService 的历史绑定。 */
    public void bind(String userId, CallPoint playerService) {
        if (userId == null || userId.isBlank() || playerService == null) {
            return;
        }
        Binding binding = bindings.get(userId);
        if (binding == null) {
            bindings.put(userId, new Binding(new CallPoint(playerService)));
            return;
        }
        binding.setService(new CallPoint(playerService));
    }

    /** PlayerService 进入正式路由后，从其 MDB 恢复仍保留的历史绑定。 */
    public void onServiceConnectReady(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service.getServiceType() != ServiceType.PLAYER) {
                continue;
            }
            CallPoint playerService = service.getCallPoint();
            RpcResult<List<String>> result = PlayerServiceRpcProxy.callGetMdbPlayerUserIds(playerService);
            if (!result.isSuccess()) {
                LogCore.core.error("OnlineService 恢复 PlayerService 历史绑定失败: playerService={}, errorCode={}, message={}",
                        playerService, result.getErrorCode(), result.getErrorMessage());
                continue;
            }
            for (String userId : result.getValue()) {
                bind(userId, playerService);
            }
            LogCore.core.info("OnlineService 恢复 PlayerService 历史绑定: playerService={}, count={}",
                    playerService, result.getValue().size());
        }
    }

    /** PlayerService 断开后，立即删除指向该服务的全部历史绑定。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service.getServiceType() != ServiceType.PLAYER) {
                continue;
            }
            CallPoint playerService = service.getCallPoint();
            int removedCount = 0;
            for (Iterator<Map.Entry<String, Binding>> iterator = bindings.entrySet().iterator();
                 iterator.hasNext(); ) {
                Map.Entry<String, Binding> entry = iterator.next();
                if (playerService.equals(entry.getValue().service())) {
                    iterator.remove();
                    removedCount++;
                }
            }
            LogCore.core.info("OnlineService 删除断开 PlayerService 的历史绑定: playerService={}, count={}",
                    playerService, removedCount);
        }
    }

    /** 仅删除仍指向指定 PlayerService 的历史绑定。 */
    public boolean remove(String userId, CallPoint expectedPlayerService) {
        Binding binding = bindings.get(userId);
        if (binding == null || expectedPlayerService == null
                || !expectedPlayerService.equals(binding.service())) {
            return false;
        }
        return bindings.remove(userId, binding);
    }

    /** 返回用户当前绑定的 PlayerService 地址副本。 */
    public CallPoint get(String userId) {
        Binding binding = bindings.get(userId);
        return binding == null ? null : new CallPoint(binding.service());
    }

    /** 返回当前保存的用户到 PlayerService 绑定数量。 */
    public int size() {
        return bindings.size();
    }
}
