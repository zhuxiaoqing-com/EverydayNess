package org.evd.game.OnlineService.session;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Db.table.MdbPlayerManager;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.util.TimeUtils;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * OnlineService 中 userId 到 PlayerService 的绑定。
 *
 * <p>PlayerService 断开时无法判断是网络抖动还是机器故障，因此不立即删除历史绑定，
 * 而是按照 {@code MdbPlayerManager.FLUSH_DELAY_MILLIS + 10分钟} 设置过期时间。
 * 在这段保留时间内，玩家继续锁定在原 PlayerService 上，不允许重新进入其他 PlayerService；
 * 如果只是 PlayerService 重启，服务恢复后会重新建立绑定并解除过期时间。</p>
 *
 * <p>正常缩容流程会提前删除玩家数据，随后再发生的服务断开不会留下需要处理的历史绑定，
 * 因此这里的断线保留机制只处理网络异常或机器故障场景。</p>
 */
public final class UserIdPlayerServiceMap {
    private static final long NO_EXPIRE = -1L;
    private static final long OFFLINE_EXPIRE_MILLIS =
            MdbPlayerManager.FLUSH_DELAY_MILLIS + 10 * TimeUtils.MIN;

    private static final class Binding {
        private CallPoint service;
        private long expireAt;

        private Binding(CallPoint service) {
            this.service = service;
            this.expireAt = NO_EXPIRE;
        }

        private CallPoint service() {
            return service;
        }

        private void setService(CallPoint service) {
            this.service = service;
        }

        private long expireAt() {
            return expireAt;
        }

        private void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Binding> bindings = new HashMap<>();

    /**
     * 建立或刷新用户到 PlayerService 的历史绑定，并取消离线过期。
     * 这里还不能主动给离线时间;因为万一那个PlayerService序列化进入一直失败呢？
     * 如果PlayerService离线期间，那没办法;必须要删除一下;毕竟再次上线也会重新获取的;
     * 那playerService离线直接就删了，不就好吗;好像没啥问题啊;
     * 不能删，删了，玩家不就立马选其他PlayerService运行了吗;
     */
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
        binding.setExpireAt(NO_EXPIRE);
    }

    /** 批量恢复 PlayerService 当前 MDB 中仍保留的玩家历史绑定。 */
    public int bindAll(Collection<String> userIds, CallPoint playerService) {
        if (playerService == null) {
            return 0;
        }

        for (Iterator<Map.Entry<String, Binding>> iterator = bindings.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, Binding> entry = iterator.next();
            if (playerService.equals(entry.getValue().service())) {
                iterator.remove();
                LogCore.core.info("OnlineService 删除 PlayerService 历史绑定: userId={}, playerService={}",
                        entry.getKey(), playerService);
            }
        }

        if (userIds == null) {
            return 0;
        }
        int bound = 0;
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            bind(userId, playerService);
            LogCore.core.info("OnlineService 绑定 PlayerService 历史用户: userId={}, playerService={}",
                    userId, playerService);
            bound++;
        }
        return bound;
    }

    /** PlayerService 断开后，设置指向该服务的历史绑定过期时间，不立即删除。 */
    public void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        for (RegisteredService service : serviceList) {
            if (service.getServiceType() != ServiceType.PLAYER) {
                continue;
            }
            CallPoint playerService = service.getCallPoint();
            long expireAt = Service.getTime() + OFFLINE_EXPIRE_MILLIS;
            int scheduledCount = 0;
            for (Binding binding : bindings.values()) {
                if (playerService.equals(binding.service())) {
                    binding.setExpireAt(expireAt);
                    scheduledCount++;
                }
            }
            LogCore.core.info("OnlineService 设置断开 PlayerService 历史绑定过期时间: playerService={}, expireAt={}, count={}",
                    playerService, expireAt, scheduledCount);
        }
    }

    /** 周期清理已经到期的 PlayerService 历史绑定。 */
    public void expire(long currentTime) {
        int expiredCount = 0;
        for (Iterator<Map.Entry<String, Binding>> iterator = bindings.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, Binding> entry = iterator.next();
            Binding binding = entry.getValue();
            if (binding.expireAt() != NO_EXPIRE && binding.expireAt() <= currentTime) {
                LogCore.core.info("OnlineService 清理过期 PlayerService 历史绑定: userId={}, playerService={}, expireAt={}, currentTime={}",
                        entry.getKey(), binding.service(), binding.expireAt(), currentTime);
                iterator.remove();
                expiredCount++;
            }
        }
        if (expiredCount > 0) {
            LogCore.core.info("OnlineService 清理已过期的 PlayerService 历史绑定: count={}, currentTime={}",
                    expiredCount, currentTime);
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
