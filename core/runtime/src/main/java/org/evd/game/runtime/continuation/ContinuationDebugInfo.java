package org.evd.game.runtime.continuation;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.RpcCallBase;
import org.evd.game.runtime.ymlconfig.RegisteredService;

public final class ContinuationDebugInfo {
    private ContinuationDebugInfo() {
    }

    public static abstract class DebugInfo {
        @Override
        public abstract String toString();
    }

    public static abstract class RpcWaitDebugInfo extends DebugInfo {
        private final RpcCallBase rpcCallBase;

        protected RpcWaitDebugInfo(RpcCallBase rpcCallBase) {
            this.rpcCallBase = rpcCallBase;
        }

        public abstract CallPoint getTargetCallPoint();

        public final RpcCallBase getRpcCallBase() {
            return rpcCallBase;
        }

        public final long getOutboundSessionId() {
            RegisteredService targetService = rpcCallBase.getTargetRegisteredService();
            return targetService == null ? 0L : targetService.getOutboundSessionId();
        }
    }

    public static final class RpcDebugInfo extends DebugInfo {
        private final int rpcMethodKey;
        private final int dispatchType;

        public RpcDebugInfo(RpcCallBase rpcCallBase) {
            this.dispatchType = rpcCallBase.getDispatchType();
            this.rpcMethodKey = rpcCallBase.getMethodKey();
        }

        @Override
        public String toString() {
            return
                    "rpcMethodKey=" + rpcMethodKey +
                    ", dispatchType=" + dispatchType
                    ;
        }
    }

    public static final class WaitTimeoutDebugInfo extends DebugInfo {
        private final long timeoutMillis;

        public WaitTimeoutDebugInfo(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "timeoutMillis=" + timeoutMillis;
        }
    }

    public static final class SleepDebugInfo extends DebugInfo {
        private final long delayMillis;

        public SleepDebugInfo(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public String toString() {
            return "sleep delayMillis=" + delayMillis;
        }
    }

    public static final class ServiceRpcWaitDebugInfo extends RpcWaitDebugInfo {
        private final int methodKey;
        private final long timeoutMillis;

        public ServiceRpcWaitDebugInfo(RpcCallBase call, long timeoutMillis) {
            super(call);
            this.methodKey = call.getMethodKey();
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public CallPoint getTargetCallPoint() {
            CallPoint targetCallPoint = getRpcCallBase().getTo();
            return targetCallPoint == null ? null : new CallPoint(targetCallPoint);
        }

        @Override
        public String toString() {
            return "serviceRpc target=" + getTargetCallPoint() + ", methodKey=" + methodKey
                    + ", timeoutMillis=" + timeoutMillis + ", sessionId=" + getOutboundSessionId();
        }
    }

    public static final class ActorRpcWaitDebugInfo extends RpcWaitDebugInfo {
        private final ActorId actorId;
        private final ActorAddress actorAddress;
        private final int methodKey;
        private final long timeoutMillis;

        public ActorRpcWaitDebugInfo(RpcCallBase rpcCallBase,
                                     ActorId actorId,
                                     ActorAddress actorAddress,
                                     int methodKey,
                                     long timeoutMillis) {
            super(rpcCallBase);
            this.actorId = actorId == null ? null : new ActorId(actorId);
            this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
            this.methodKey = methodKey;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public CallPoint getTargetCallPoint() {
            CallPoint callPoint = actorAddress == null ? null : actorAddress.getCallPoint();
            return callPoint == null ? null : new CallPoint(callPoint);
        }

        @Override
        public String toString() {
            return "actorRpc actorId=" + actorId + ", actorAddress=" + actorAddress
                    + ", methodKey=" + methodKey + ", timeoutMillis=" + timeoutMillis
                    + ", sessionId=" + getOutboundSessionId();
        }
    }

    public static final class CompletionStageWaitDebugInfo extends DebugInfo {
        private final Class<?> stageType;
        private final long timeoutMillis;

        public CompletionStageWaitDebugInfo(Class<?> stageType, long timeoutMillis) {
            this.stageType = stageType;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "completionStage type=" + (stageType == null ? "null" : stageType.getName())
                    + ", timeoutMillis=" + timeoutMillis;
        }
    }

    public static final class LockWaitDebugInfo extends DebugInfo {
        private final LockType lockType;
        private final Object lockKey;
        private final long timeoutMillis;

        public LockWaitDebugInfo(LockType lockType, Object lockKey, long timeoutMillis) {
            this.lockType = lockType;
            this.lockKey = lockKey;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "lock type=" + lockType + ", key=" + lockKey + ", timeoutMillis=" + timeoutMillis;
        }
    }

    public static final class LocationLockWaitDebugInfo extends DebugInfo {
        private final ActorId actorId;
        private final ActorAddress actorAddress;
        private final long timeoutMillis;

        public LocationLockWaitDebugInfo(ActorId actorId, ActorAddress actorAddress, long timeoutMillis) {
            this.actorId = actorId == null ? null : new ActorId(actorId);
            this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "location lock actorId=" + actorId + ", actorAddress=" + actorAddress
                    + ", timeoutMillis=" + timeoutMillis;
        }
    }
}
