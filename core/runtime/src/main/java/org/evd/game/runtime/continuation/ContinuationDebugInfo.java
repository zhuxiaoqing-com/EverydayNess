package org.evd.game.runtime.continuation;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.CallPoint;

public final class ContinuationDebugInfo {
    private ContinuationDebugInfo() {
    }

    public static abstract class DebugInfo {
        @Override
        public abstract String toString();
    }

    public static final class RpcDebugInfo extends DebugInfo {
        private final int rpcMethodKey;

        public RpcDebugInfo(int rpcMethodKey) {
            this.rpcMethodKey = rpcMethodKey;
        }

        @Override
        public String toString() {
            return "rpcMethodKey=" + rpcMethodKey;
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

    public static final class ServiceRpcWaitDebugInfo extends DebugInfo {
        private final CallPoint targetCallPoint;
        private final int methodKey;
        private final long timeoutMillis;

        public ServiceRpcWaitDebugInfo(CallPoint targetCallPoint, int methodKey, long timeoutMillis) {
            this.targetCallPoint = new CallPoint(targetCallPoint);
            this.methodKey = methodKey;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "serviceRpc target=" + targetCallPoint + ", methodKey=" + methodKey + ", timeoutMillis=" + timeoutMillis;
        }
    }

    public static final class ActorRpcWaitDebugInfo extends DebugInfo {
        private final ActorId actorId;
        private final ActorAddress actorAddress;
        private final int methodKey;
        private final long timeoutMillis;

        public ActorRpcWaitDebugInfo(ActorId actorId, ActorAddress actorAddress, int methodKey, long timeoutMillis) {
            this.actorId = actorId == null ? null : new ActorId(actorId);
            this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
            this.methodKey = methodKey;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "actorRpc actorId=" + actorId + ", actorAddress=" + actorAddress
                    + ", methodKey=" + methodKey + ", timeoutMillis=" + timeoutMillis;
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
        private final int lockType;
        private final Object lockKey;
        private final long timeoutMillis;

        public LockWaitDebugInfo(int lockType, Object lockKey, long timeoutMillis) {
            this.lockType = lockType;
            this.lockKey = lockKey;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String toString() {
            return "lock type=" + lockType + ", key=" + lockKey + ", timeoutMillis=" + timeoutMillis;
        }
    }
}
