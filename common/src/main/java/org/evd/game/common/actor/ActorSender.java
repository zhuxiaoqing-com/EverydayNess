package org.evd.game.common.actor;

import org.evd.game.common.proxy.LocationServiceProxy;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;

import java.util.HashMap;
import java.util.Map;

public class ActorSender {
    private static final int RETRY_TIMES = 20;
    private static final long RETRY_INTERVAL_MILLIS = 500L;

    @FunctionalInterface
    public interface ActorCaller<T> {
        T call(CallPoint callPoint, ActorId actorId);
    }

    private final Map<ActorId, CallPoint> actorLocations = new HashMap<>();

    public Target target(ActorId actorId) {
        return new Target(actorId);
    }

    public Target player(long playerId) {
        return target(ActorId.player(playerId));
    }

    public Target map(long mapId) {
        return target(ActorId.map(mapId));
    }

    public Target gate(long gateId) {
        return target(ActorId.gate(gateId));
    }

    public Target guild(long guildId) {
        return target(ActorId.guild(guildId));
    }

    public Target mapPlayer(long playerId) {
        return target(ActorId.mapPlayer(playerId));
    }

    public final class Target {
        private final ActorId actorId;

        private Target(ActorId actorId) {
            this.actorId = new ActorId(actorId);
        }

        public ActorId actorId() {
            return new ActorId(actorId);
        }

        public CallPoint get() {
            CallPoint callPoint = actorLocations.get(actorId);
            return callPoint == null ? null : new CallPoint(callPoint);
        }

        public CallPoint getOrQuery() {
            CallPoint cached = this.get();
            if (cached != null) {
                return cached;
            }

            CallPoint remote = LocationServiceProxy.inst().getActor(actorId);
            if (remote == null) {
                return null;
            }

            this.cache(remote);
            return new CallPoint(remote);
        }

        public void cache(CallPoint callPoint) {
            actorLocations.put(new ActorId(actorId), new CallPoint(callPoint));
        }

        public void remove() {
            actorLocations.remove(actorId);
        }

        public CallPoint refresh() {
            CallPoint remote = LocationServiceProxy.inst().getActor(actorId);
            if (remote == null) {
                actorLocations.remove(actorId);
                return null;
            }
            actorLocations.put(new ActorId(actorId), new CallPoint(remote));
            return new CallPoint(remote);
        }

        public <T> T call(CallPoint callPoint, ActorCaller<T> caller) {
            return caller.call(new CallPoint(callPoint), new ActorId(actorId));
        }

        public void call(CallPoint callPoint, int methodKey, Object[] params) {
            Service.getCurrent().call(callPoint, actorId, methodKey, params);
        }

        public <T> T call(String serviceClassName, long routeKey, ActorCaller<T> caller) {
            return this.call(resolveCallPoint(serviceClassName, routeKey), caller);
        }

        public void call(String serviceClassName, long routeKey, int methodKey, Object[] params) {
            this.call(resolveCallPoint(serviceClassName, routeKey), methodKey, params);
        }

        public <T> T callWithRetry(ActorCaller<T> caller) {
            int failTimes = 0;

            while (true) {
                CallPoint callPoint = this.get();
                if (callPoint == null) {
                    callPoint = this.refresh();
                }
                if (callPoint == null) {
                    throw RpcCallException.actorNotFound(actorId);
                }

                try {
                    return caller.call(new CallPoint(callPoint), new ActorId(actorId));
                } catch (RuntimeException exception) {
                    if (!refreshIfActorNotFound(actorId, exception)) {
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
            this.callWithRetry((callPoint, actorId) -> {
                Service.getCurrent().call(callPoint, actorId, methodKey, params);
                return null;
            });
        }

        private CallPoint resolveCallPoint(String serviceClassName, long routeKey) {
            CallPoint callPoint = DistributeConfig.getNodeByServiceClass(serviceClassName, routeKey);
            if (callPoint == null) {
                throw new IllegalStateException("找不到 actor 目标服务: serviceClass=" + serviceClassName + ", routeKey=" + routeKey);
            }
            return callPoint;
        }
    }

    private boolean refreshIfActorNotFound(ActorId actorId, RuntimeException exception) {
        if (!(exception instanceof RpcCallException rpcCallException)) {
            return false;
        }
        int errorCode = rpcCallException.getErrorCode();
        if (errorCode != RpcErrorCodes.ACTOR_NOT_FOUND) {
            return false;
        }
        target(actorId).refresh();
        return true;
    }
}
