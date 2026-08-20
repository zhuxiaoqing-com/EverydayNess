package org.evd.game.OnlineService.session;

import org.evd.game.runtime.call.CallPoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** OnlineService 中 userId 到 ConnService 的绑定。 */
public final class UserIdConnServiceMap {
    public static final long NO_TIMEOUT = -1L;
    private static final long OFFLINE_TIMEOUT_MILLIS = 5 * 60 * 1000L;

    private static final class Binding {
        private CallPoint service;
        private long timeoutAt;

        private Binding(CallPoint service) {
            this.service = service;
            this.timeoutAt = NO_TIMEOUT;
        }

        private CallPoint service() {
            return service;
        }

        private void setService(CallPoint service) {
            this.service = service;
        }

        private long timeoutAt() {
            return timeoutAt;
        }

        private void setTimeoutAt(long timeoutAt) {
            this.timeoutAt = timeoutAt;
        }
    }

    private final Map<String, Binding> bindings = new HashMap<>();

    /** 建立或刷新用户到 ConnService 的绑定，并取消离线超时。 */
    public void bind(String userId, CallPoint connService) {
        if (userId == null || userId.isBlank() || connService == null) {
            return;
        }
        Binding binding = bindings.get(userId);
        if (binding == null) {
            bindings.put(userId, new Binding(new CallPoint(connService)));
            return;
        }
        binding.setService(new CallPoint(connService));
        binding.setTimeoutAt(NO_TIMEOUT);
    }

    /** 返回用户当前绑定的 ConnService 地址副本。 */
    public CallPoint get(String userId) {
        Binding binding = bindings.get(userId);
        return binding == null ? null : new CallPoint(binding.service());
    }

    /** 返回用户绑定的离线清理截止时间。 */
    public long getTimeoutAt(String userId) {
        Binding binding = bindings.get(userId);
        return binding == null ? NO_TIMEOUT : binding.timeoutAt();
    }

    public void tick(long now, Predicate<String> isPlayerOffline) {
        Objects.requireNonNull(isPlayerOffline, "isPlayerOffline");
        for (Iterator<Map.Entry<String, Binding>> iterator = bindings.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Binding> entry = iterator.next();
            Binding binding = entry.getValue();
            if (!isPlayerOffline.test(entry.getKey())) {
                if (binding.timeoutAt() != NO_TIMEOUT) {
                    binding.setTimeoutAt(NO_TIMEOUT);
                }
                continue;
            }
            if (binding.timeoutAt() == NO_TIMEOUT) {
                binding.setTimeoutAt(now + OFFLINE_TIMEOUT_MILLIS);
            } else if (binding.timeoutAt() <= now) {
                iterator.remove();
            }
        }
    }

    /** 返回当前保存的用户到 ConnService 绑定数量。 */
    public int size() {
        return bindings.size();
    }
}
