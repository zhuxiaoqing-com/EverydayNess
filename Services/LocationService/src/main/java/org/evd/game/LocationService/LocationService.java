package org.evd.game.LocationService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@Actor(single = true)
public class LocationService extends Service {
    private static final int COROUTINE_LOCK_TYPE_LOCATION = 3;

    private static final class LockInfo {
        private final ActorAddress lockActorAddress;
        private final Task.ContinuationWrapper lockContinuation;
        private final long revision;
        private final long timerId;

        private LockInfo(ActorAddress lockActorAddress,
                         Task.ContinuationWrapper lockContinuation,
                         long revision,
                         long timerId) {
            this.lockActorAddress = lockActorAddress;
            this.lockContinuation = lockContinuation;
            this.revision = revision;
            this.timerId = timerId;
        }
    }

    private final Map<ActorId, ActorAddress> actorLocations = new HashMap<>();
    private final Map<ActorId, LockInfo> lockInfos = new HashMap<>();
    private long nextLockRevision = 1L;

    public LocationService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Rpc
    public void add(ActorId actorId, ActorAddress actorAddress) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            addNow(actorId, actorAddress);
        }
    }

    @Rpc
    public void remove(ActorId actorId) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            removeNow(actorId);
        }
    }

    @Rpc
    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        if (actorId == null || oldActorAddress == null) {
            return;
        }
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            lockNow(actorId, oldActorAddress, timeMillis);
        }
    }

    @Rpc
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
        resumeContinuation(lockInfo.lockContinuation, null);
    }

    @Rpc
    public ActorAddress get(ActorId actorId) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            return getNow(actorId);
        }
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
        ActorId key = copyActorId(actorId);
        ActorAddress lockActorAddress = copyAddress(oldActorAddress);
        Task.ContinuationWrapper lockContinuation = currentContinuation();
        long revision = nextLockRevision++;
        long timerId = timeMillis > 0
                ? newOnceTimer(timeMillis, () -> onLockTimeout(key, revision))
                : 0L;

        lockInfos.put(key, new LockInfo(lockActorAddress, lockContinuation, revision, timerId));
        LogCore.core.info("LocationService 锁定actor: actorId={}, address={}, timeMillis={}",
                actorId, oldActorAddress, timeMillis);
        lockContinuation.prepareWait();
        lockContinuation.waitResult();
    }

    private ActorAddress getNow(ActorId actorId) {
        if (actorId == null) {
            return null;
        }
        ActorAddress actorAddress = actorLocations.get(copyActorId(actorId));
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

    private ContinuationLockScope awaitLocationLockScope(ActorId actorId) {
        return awaitCoroutineLockScope(COROUTINE_LOCK_TYPE_LOCATION, copyActorId(actorId));
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
        return left.getMailBoxEpoch() == right.getMailBoxEpoch()
                && left.getCallPoint().getNodeId().equals(right.getCallPoint().getNodeId())
                && left.getCallPoint().getServId().equals(right.getCallPoint().getServId());
    }
}
