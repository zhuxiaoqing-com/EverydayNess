package org.evd.game.common.mailbox;

import org.evd.game.common.proxy.LocationServiceProxy;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailboxKey;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

import java.util.HashMap;
import java.util.Map;

public class MailboxSender {
    private static final int RETRY_TIMES = 20;
    private static final long RETRY_INTERVAL_MILLIS = 500L;

    @FunctionalInterface
    public interface MailboxCaller<T> {
        T call(CallPoint callPoint, MailboxKey mailboxKey);
    }

    private final Map<MailboxKey, CallPoint> mailboxLocations = new HashMap<>();

    public Target target(MailboxKey mailboxKey) {
        return new Target(mailboxKey);
    }

    public Target player(long playerId) {
        return target(MailboxKey.player(playerId));
    }

    public Target map(long mapId) {
        return target(MailboxKey.map(mapId));
    }

    public Target gate(long gateId) {
        return target(MailboxKey.gate(gateId));
    }

    public final class Target {
        private final MailboxKey mailboxKey;

        private Target(MailboxKey mailboxKey) {
            this.mailboxKey = new MailboxKey(mailboxKey);
        }

        public MailboxKey key() {
            return new MailboxKey(mailboxKey);
        }

        public CallPoint get() {
            CallPoint callPoint = mailboxLocations.get(mailboxKey);
            return callPoint == null ? null : new CallPoint(callPoint);
        }

        public CallPoint getOrQuery() {
            CallPoint cached = this.get();
            if (cached != null) {
                return cached;
            }

            CallPoint remote = LocationServiceProxy.inst().getMailbox(mailboxKey);
            if (remote == null) {
                return null;
            }

            this.cache(remote);
            return new CallPoint(remote);
        }

        public void cache(CallPoint callPoint) {
            mailboxLocations.put(new MailboxKey(mailboxKey), new CallPoint(callPoint));
        }

        public void remove() {
            mailboxLocations.remove(mailboxKey);
        }

        public CallPoint refresh() {
            CallPoint remote = LocationServiceProxy.inst().getMailbox(mailboxKey);
            if (remote == null) {
                mailboxLocations.remove(mailboxKey);
                return null;
            }
            mailboxLocations.put(new MailboxKey(mailboxKey), new CallPoint(remote));
            return new CallPoint(remote);
        }

        public <T> T call(CallPoint callPoint, MailboxCaller<T> caller) {
            return caller.call(new CallPoint(callPoint), new MailboxKey(mailboxKey));
        }

        public void call(CallPoint callPoint, int methodKey, Object[] params) {
            Service.getCurrent().call(callPoint, new MailboxKey(mailboxKey), methodKey, params);
        }

        public <T> T call(String serviceClassName, long routeKey, MailboxCaller<T> caller) {
            return this.call(resolveCallPoint(serviceClassName, routeKey), caller);
        }

        public void call(String serviceClassName, long routeKey, int methodKey, Object[] params) {
            this.call(resolveCallPoint(serviceClassName, routeKey), methodKey, params);
        }

        public <T> T callWithRetry(MailboxCaller<T> caller) {
            int failTimes = 0;

            while (true) {
                CallPoint callPoint = this.get();
                if (callPoint == null) {
                    callPoint = this.refresh();
                }
                if (callPoint == null) {
                    throw RpcCallException.mailboxNotFound(mailboxKey);
                }

                try {
                    return caller.call(new CallPoint(callPoint), new MailboxKey(mailboxKey));
                } catch (RuntimeException exception) {
                    if (!refreshIfMailboxNotFound(mailboxKey, exception)) {
                        throw exception;
                    }

                    ++failTimes;
                    if (failTimes > RETRY_TIMES) {
                        this.remove();
                        throw exception;
                    }

                    Service.getCurrent().sleep(RETRY_INTERVAL_MILLIS);
                }
            }
        }

        public void callWithRetry(int methodKey, Object[] params) {
            this.callWithRetry((callPoint, key) -> {
                Service.getCurrent().call(callPoint, key, methodKey, params);
                return null;
            });
        }

        private CallPoint resolveCallPoint(String serviceClassName, long routeKey) {
            CallPoint callPoint = DistributeConfig.getNodeByServiceClass(serviceClassName, routeKey);
            if (callPoint == null) {
                throw new IllegalStateException("找不到 mailbox 目标服务: serviceClass=" + serviceClassName + ", routeKey=" + routeKey);
            }
            return callPoint;
        }
    }

    private boolean refreshIfMailboxNotFound(MailboxKey mailboxKey, RuntimeException exception) {
        if (!(exception instanceof RpcCallException rpcCallException)) {
            return false;
        }
        int errorCode = rpcCallException.getErrorCode();
        if (errorCode != RpcErrorCodes.MAILBOX_NOT_FOUND && errorCode != RpcErrorCodes.ACTOR_NOT_FOUND) {
            return false;
        }
        target(mailboxKey).refresh();
        return true;
    }
}
