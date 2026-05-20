package org.evd.game.LocationService;

import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.Task;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

@Actor(single = true)
public class LocationService extends Service {
    private enum PendingOperationType {
        ADD,
        REMOVE,
        GET,
        LOCK
    }

    private static final Object EXECUTE_INLINE = new Object();

    private static final class PendingOperation {
        private final PendingOperationType type;
        private final Task.ContinuationWrapper continuation;
        private final ActorAddress actorAddress;
        private final int timeoutMillis;

        private PendingOperation(PendingOperationType type,
                                 Task.ContinuationWrapper continuation,
                                 ActorAddress actorAddress,
                                 int timeoutMillis) {
            this.type = type;
            this.continuation = continuation;
            this.actorAddress = actorAddress == null ? null : new ActorAddress(actorAddress);
            this.timeoutMillis = timeoutMillis;
        }
    }

    private static final class LockInfo {
        private final ActorAddress lockActorAddress;
        private final long revision;
        private final long timerId;

        private LockInfo(ActorAddress lockActorAddress, long revision, long timerId) {
            this.lockActorAddress = lockActorAddress;
            this.revision = revision;
            this.timerId = timerId;
        }
    }

    private final Map<ActorId, ActorAddress> actorLocations = new HashMap<>();
    private final Map<ActorId, LockInfo> lockInfos = new HashMap<>();
    // ET 的 location lock 会跨 RPC 持有，这里把被挡住的请求挂起，等 unlock 后再按顺序恢复。
    private final Map<ActorId, ArrayDeque<PendingOperation>> pendingOperations = new HashMap<>();
    private long nextLockRevision = 1L;

    public LocationService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }

    public LocationService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
    }

    public void add(ActorId actorId, ActorAddress actorAddress) {
        Object waited = awaitIfLocked(actorId, PendingOperationType.ADD, actorAddress, 0);
        if (waited != EXECUTE_INLINE) {
            return;
        }
        addNow(actorId, actorAddress);
    }

    public void remove(ActorId actorId) {
        Object waited = awaitIfLocked(actorId, PendingOperationType.REMOVE, null, 0);
        if (waited != EXECUTE_INLINE) {
            return;
        }
        removeNow(actorId);
    }

    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        Object waited = awaitIfLocked(actorId, PendingOperationType.LOCK, oldActorAddress, timeMillis);
        if (waited != EXECUTE_INLINE) {
            return;
        }
        lockNow(actorId, oldActorAddress, timeMillis);
    }

    public void unlock(ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress) {
        if (actorId == null) {
            return;
        }

        ActorId key = copyActorId(actorId);
        LockInfo lockInfo = lockInfos.get(key);
        if (lockInfo == null) {
            LogCore.core.error("LocationService 解锁失败，未找到锁: actorId={}, oldAddress={}",
                    actorId, oldActorAddress);
            return;
        }
        if (!sameAddress(lockInfo.lockActorAddress, oldActorAddress)) {
            LogCore.core.error("LocationService 解锁失败，锁宿主不匹配: actorId={}, oldAddress={}, lockAddress={}",
                    actorId, oldActorAddress, lockInfo.lockActorAddress);
            return;
        }

        if (lockInfo.timerId != 0L) {
            removeTimer(lockInfo.timerId);
        }

        if (newActorAddress == null) {
            actorLocations.remove(key);
        } else {
            actorLocations.put(key, copyAddress(newActorAddress));
        }
        lockInfos.remove(key);

        LogCore.core.info("LocationService 解锁actor: actorId={}, oldAddress={}, newAddress={}",
                actorId, oldActorAddress, newActorAddress);
        drainPendingOperations(key);
    }

    public ActorAddress get(ActorId actorId) {
        Object waited = awaitIfLocked(actorId, PendingOperationType.GET, null, 0);
        if (waited != EXECUTE_INLINE) {
            return waited == null ? null : new ActorAddress((ActorAddress) waited);
        }
        return getNow(actorId);
    }

    private void addNow(ActorId actorId, ActorAddress actorAddress) {
        if (actorId == null || actorAddress == null) {
            return;
        }
        actorLocations.put(copyActorId(actorId), copyAddress(actorAddress));
        LogCore.core.info("LocationService 添加actor: actorId={}, address={}",
                actorId, actorAddress);
    }

    private void removeNow(ActorId actorId) {
        if (actorId == null) {
            return;
        }
        actorLocations.remove(copyActorId(actorId));
        LogCore.core.info("LocationService 移除actor: actorId={}", actorId);
    }

    private void lockNow(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        if (actorId == null || oldActorAddress == null) {
            return;
        }

        ActorId key = copyActorId(actorId);
        ActorAddress lockActorAddress = copyAddress(oldActorAddress);
        long revision = nextLockRevision++;
        long timerId = timeMillis > 0
                ? newOnceTimer(timeMillis, () -> onLockTimeout(key, revision))
                : 0L;

        lockInfos.put(key, new LockInfo(lockActorAddress, revision, timerId));
        LogCore.core.info("LocationService 锁定actor: actorId={}, address={}, timeMillis={}",
                actorId, oldActorAddress, timeMillis);
    }

    private ActorAddress getNow(ActorId actorId) {
        if (actorId == null) {
            return null;
        }
        ActorAddress actorAddress = actorLocations.get(actorId);
        return actorAddress == null ? null : new ActorAddress(actorAddress);
    }

    private void onLockTimeout(ActorId actorId, long revision) {
        LockInfo lockInfo = lockInfos.get(actorId);
        if (lockInfo == null || lockInfo.revision != revision) {
            return;
        }

        LogCore.core.info("LocationService 锁超时释放: actorId={}, address={}",
                actorId, lockInfo.lockActorAddress);
        unlock(actorId, lockInfo.lockActorAddress, lockInfo.lockActorAddress);
    }

    private Object awaitIfLocked(ActorId actorId,
                                 PendingOperationType type,
                                 ActorAddress actorAddress,
                                 int timeoutMillis) {
        if (actorId == null) {
            return EXECUTE_INLINE;
        }

        ActorId key = copyActorId(actorId);
        if (!lockInfos.containsKey(key)) {
            return EXECUTE_INLINE;
        }

        PendingOperation pendingOperation = new PendingOperation(
                type,
                currentContinuation(),
                actorAddress,
                timeoutMillis);
        pendingOperations.computeIfAbsent(key, ignore -> new ArrayDeque<>()).addLast(pendingOperation);

        Task.ContinuationWrapper continuation = pendingOperation.continuation;
        continuation.prepareWait();
        return continuation.waitResult();
    }

    private void drainPendingOperations(ActorId actorId) {
        ArrayDeque<PendingOperation> queue = pendingOperations.get(actorId);
        while (queue != null && !queue.isEmpty() && !lockInfos.containsKey(actorId)) {
            PendingOperation pendingOperation = queue.pollFirst();
            resumePendingOperation(actorId, pendingOperation);
            if (queue.isEmpty()) {
                pendingOperations.remove(actorId);
                return;
            }
            if (lockInfos.containsKey(actorId)) {
                return;
            }
        }
    }

    private void resumePendingOperation(ActorId actorId, PendingOperation pendingOperation) {
        try {
            Object result = switch (pendingOperation.type) {
                case ADD -> {
                    addNow(actorId, pendingOperation.actorAddress);
                    yield null;
                }
                case REMOVE -> {
                    removeNow(actorId);
                    yield null;
                }
                case GET -> getNow(actorId);
                case LOCK -> {
                    lockNow(actorId, pendingOperation.actorAddress, pendingOperation.timeoutMillis);
                    yield null;
                }
            };
            resumeContinuation(pendingOperation.continuation, result);
        } catch (RuntimeException exception) {
            failContinuation(pendingOperation.continuation, exception);
        }
    }

    private ActorId copyActorId(ActorId actorId) {
        return actorId == null ? null : new ActorId(actorId);
    }

    private ActorAddress copyAddress(ActorAddress actorAddress) {
        return actorAddress == null ? null : new ActorAddress(actorAddress);
    }

    private boolean sameAddress(ActorAddress left, ActorAddress right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getCallPoint() == null || right.getCallPoint() == null) {
            return false;
        }
        return left.getOwnerInstanceId() == right.getOwnerInstanceId()
                && left.getMailBoxInstanceId() == right.getMailBoxInstanceId()
                && left.getCallPoint().getNodeId().equals(right.getCallPoint().getNodeId())
                && left.getCallPoint().getServId().equals(right.getCallPoint().getServId());
    }
}
